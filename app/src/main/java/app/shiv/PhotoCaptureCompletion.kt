package app.shiv

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns the terminal cleanup for one captured image. The caller must not finish until
 * asynchronous image processing has stopped reading the underlying media image.
 */
internal class PhotoCaptureCompletion(
    private val closeImage: () -> Unit,
    private val onComplete: (Throwable?) -> Unit
) {
    private val completed = AtomicBoolean(false)

    /**
     * Runs success-side effects only when this call owns terminal completion.
     * Exceptions from onComplete deliberately propagate; terminal ownership and
     * the image-close attempt remain committed, and success effects are skipped.
     */
    fun finishSuccess(onSuccess: () -> Unit): Boolean {
        if (!finish()) return false
        onSuccess()
        return true
    }

    fun finish(error: Throwable? = null): Boolean {
        if (!completed.compareAndSet(false, true)) return false
        var finalError = error
        try {
            closeImage()
        } catch (closeError: Exception) {
            if (finalError == null) finalError = closeError
        }
        onComplete(finalError)
        return true
    }
}
