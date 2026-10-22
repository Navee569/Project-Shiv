package app.shiv

/**
 * Owns image release and capture completion as separate, thread-safe transitions.
 *
 * The image may be released as soon as ML Kit task terminality is established. The UI/capture
 * completion callback may happen later. A close failure is retained and reported at final
 * completion even when release was attempted earlier.
 */
internal class PhotoCaptureCompletion(
    private val closeImage: () -> Unit,
    private val onComplete: (Throwable?) -> Unit
) {
    private val lock = Any()
    private var imageReleaseAttempted = false
    private var completionStarted = false
    private var imageCloseFailure: Exception? = null

    private data class FinishOutcome(val error: Throwable?)

    /**
     * Attempts ImageProxy release once. Returns a close failure only to the caller that performs
     * the attempt; the failure is retained and included in the eventual completion callback.
     */
    fun releaseImage(): Throwable? = synchronized(lock) {
        releaseImageLocked()
    }

    private fun releaseImageLocked(): Throwable? {
        if (imageReleaseAttempted) return null
        imageReleaseAttempted = true
        return try {
            closeImage()
            null
        } catch (failure: Exception) {
            imageCloseFailure = failure
            failure
        }
    }

    /**
     * Runs success effects only if this call owns completion and no processing/cleanup error
     * exists. If onComplete throws, terminal state remains committed and success effects are skipped.
     */
    fun finishSuccess(onSuccess: () -> Unit): Boolean {
        val outcome = finishInternal(null) ?: return false
        if (outcome.error == null) onSuccess()
        return true
    }

    fun finish(error: Throwable? = null): Boolean = finishInternal(error) != null

    /** Null means another caller already owns completion. */
    private fun finishInternal(error: Throwable?): FinishOutcome? {
        val outcome = synchronized(lock) {
            if (completionStarted) return null
            completionStarted = true
            releaseImageLocked()
            var primary = error
            val closeFailure = imageCloseFailure
            if (closeFailure != null) {
                if (primary == null) primary = closeFailure
                else if (primary !== closeFailure) {
                    try {
                        primary.addSuppressed(closeFailure)
                    } catch (_: Exception) {
                        // Preserve the primary processing failure if diagnostic attachment fails.
                    }
                }
            }
            FinishOutcome(primary)
        }
        onComplete(outcome.error)
        return outcome
    }
}

/**
 * Production bridge between Brain's asynchronous analysis lifecycle and CameraX capture ownership.
 * The processor calls imageConsumed only after its ML task is terminal; result/UI delivery is separate.
 */
internal class PhotoCaptureAnalysisAdapter(
    private val completion: PhotoCaptureCompletion,
    private val onDescription: (String) -> Unit,
    private val onClose: () -> Unit,
    private val onAnalysisUnresolved: (Throwable) -> Unit = {},
    private val onUiAbandoned: () -> Unit = {}
) {
    private val stateLock = Any()
    private var uiAbandoned = false
    private var resultDelivered = false

    /** Stop delivering late UI effects without violating ownership of an in-flight ML task. */
    fun abandonUi() {
        val shouldNotify = synchronized(stateLock) {
            if (uiAbandoned || resultDelivered) false
            else {
                uiAbandoned = true
                true
            }
        }
        if (shouldNotify) onUiAbandoned()
    }

    /**
     * Notify the UI that analysis exceeded a bounded wait. The image is NOT released and the
     * completion is NOT finalized; the fallback waiter continues waiting for actual task terminality.
     */
    fun analysisUnresolved(failure: Throwable) {
        val shouldNotify = synchronized(stateLock) {
            if (uiAbandoned || resultDelivered) false
            else {
                uiAbandoned = true
                true
            }
        }
        if (shouldNotify) {
            onUiAbandoned()
            onAnalysisUnresolved(failure)
        }
    }

    fun imageConsumed() {
        completion.releaseImage()?.let { throw it }
    }

    fun descriptionReady(description: String) {
        val ownsResult = synchronized(stateLock) {
            if (resultDelivered) false
            else {
                resultDelivered = true
                true
            }
        }
        if (!ownsResult) return
        completion.finishSuccess {
            val deliverUi = synchronized(stateLock) { !uiAbandoned }
            if (deliverUi) {
                onDescription(description)
                onClose()
            }
        }
    }

    fun failed(failure: Throwable) {
        val ownsResult = synchronized(stateLock) {
            if (resultDelivered) false
            else {
                resultDelivered = true
                true
            }
        }
        if (ownsResult) completion.finish(failure)
    }
}
