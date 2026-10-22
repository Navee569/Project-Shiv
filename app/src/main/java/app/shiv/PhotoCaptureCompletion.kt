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
            else if (finalError !== closeError) finalError.addSuppressed(closeError)
        }
        onComplete(finalError)
        return true
    }
}


/**
 * Production bridge between Brain's asynchronous analysis lifecycle and CameraX capture ownership.
 * The processor calls imageConsumed only after its ML task is terminal; result/UI delivery is separate.
 */
internal class PhotoCaptureAnalysisAdapter(
    private val completion: PhotoCaptureCompletion,
    private val onDescription: (String) -> Unit,
    private val onClose: () -> Unit
) {
    fun imageConsumed() {
        completion.releaseImage()?.let { throw it }
    }

    fun descriptionReady(description: String) {
        completion.finishSuccess {
            onDescription(description)
            onClose()
        }
    }

    fun failed(failure: Throwable) {
        completion.finish(failure)
    }
}
