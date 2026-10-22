package app.shiv

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeler

internal object InterruptSafeAwait {
    fun <T> await(awaitOnce: () -> T, restoreInterrupt: () -> Unit = { Thread.currentThread().interrupt() }): T {
        var interrupted = false
        try {
            while (true) {
                try {
                    return awaitOnce()
                } catch (_: InterruptedException) {
                    interrupted = true
                }
            }
        } finally {
            if (interrupted) restoreInterrupt()
        }
    }
}

internal interface PhotoLabeler<I, R> {
    fun process(image: I, onSuccess: (R) -> Unit, onFailure: (Throwable) -> Unit, onComplete: () -> Unit)
    fun close()
}

/**
 * Keeps the input alive until the ML Kit task is terminal, including listener-registration
 * failure. The exceptional fallback waits uninterruptibly, then restores the interrupt flag.
 */
internal class MlKitPhotoLabeler(private val delegate: ImageLabeler) :
    PhotoLabeler<InputImage, List<ImageLabel>> {
    override fun process(
        image: InputImage,
        onSuccess: (List<ImageLabel>) -> Unit,
        onFailure: (Throwable) -> Unit,
        onComplete: () -> Unit
    ) {
        val task = delegate.process(image)
        try {
            task.addOnCompleteListener { completed ->
                try {
                    if (completed.isSuccessful) onSuccess(completed.result ?: emptyList())
                    else onFailure(completed.exception ?: IllegalStateException("Image labeling failed without an exception"))
                } finally {
                    onComplete()
                }
            }
        } catch (registrationFailure: Throwable) {
            // Do not create a worker whose start could fail and accidentally release the image.
            // This rare exceptional path may block its caller, but retains ownership until terminality.
            try {
                InterruptSafeAwait.await(awaitOnce = { Tasks.await(task) })
                onFailure(registrationFailure)
            } catch (taskFailure: Throwable) {
                if (taskFailure !== registrationFailure) taskFailure.addSuppressed(registrationFailure)
                onFailure(taskFailure)
            } finally {
                onComplete()
            }
        }
    }

    override fun close() = delegate.close()
}

/** Outcome selection and terminal ownership are committed atomically under the same lock. */
internal object PhotoLabelingPipeline {
    fun <I, R> run(image: I, labeler: PhotoLabeler<I, R>, onResult: (Result<R>) -> Unit) {
        val lock = Any()
        var outcome: Result<R>? = null
        var terminal = false

        fun record(result: Result<R>) {
            synchronized(lock) { if (!terminal && outcome == null) outcome = result }
        }

        fun closeAndDeliver(result: Result<R>) {
            var finalResult = result
            try {
                labeler.close()
            } catch (closeFailure: Throwable) {
                val primary = finalResult.exceptionOrNull()
                if (primary == null) finalResult = Result.failure(closeFailure)
                else if (primary !== closeFailure) primary.addSuppressed(closeFailure)
            }
            onResult(finalResult)
        }

        fun finishFromCompletion() {
            val result = synchronized(lock) {
                if (terminal) return
                terminal = true
                outcome ?: Result.failure(IllegalStateException("Image labeling completed without a result"))
            }
            closeAndDeliver(result)
        }

        try {
            labeler.process(image, { record(Result.success(it)) }, { record(Result.failure(it)) }, ::finishFromCompletion)
        } catch (setupFailure: Throwable) {
            val ownsTerminal = synchronized(lock) {
                if (terminal) false else { terminal = true; true }
            }
            if (!ownsTerminal) throw setupFailure
            closeAndDeliver(Result.failure(setupFailure))
        }
    }
}
