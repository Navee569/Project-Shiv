package app.shiv

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeler
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal seam around ML Kit so processing setup, asynchronous completion, and cleanup can be
 * tested without a camera. Implementations must invoke onComplete only after image processing
 * has stopped reading the input image.
 */
internal interface PhotoLabeler<I, R> {
    fun process(
        image: I,
        onSuccess: (R) -> Unit,
        onFailure: (Throwable) -> Unit,
        onComplete: () -> Unit
    )
    fun close()
}

internal class MlKitPhotoLabeler(private val delegate: ImageLabeler) : PhotoLabeler<InputImage, List<ImageLabel>> {
    override fun process(
        image: InputImage,
        onSuccess: (List<ImageLabel>) -> Unit,
        onFailure: (Throwable) -> Unit,
        onComplete: () -> Unit
    ) {
        delegate.process(image)
            .addOnSuccessListener { onSuccess(it) }
            .addOnFailureListener { onFailure(it) }
            .addOnCompleteListener { onComplete() }
    }

    override fun close() = delegate.close()
}

/**
 * Closes the labeler before delivering a terminal result to the caller. A synchronous setup
 * exception is terminal too. Atomic terminal ownership prevents late/duplicate callbacks from
 * closing resources or notifying the caller twice.
 */
internal object PhotoLabelingPipeline {
    fun <I, R> run(image: I, labeler: PhotoLabeler<I, R>, onResult: (Result<R>) -> Unit) {
        val terminal = AtomicBoolean(false)
        val lock = Any()
        var outcome: Result<R>? = null

        fun record(result: Result<R>) {
            synchronized(lock) {
                if (outcome == null) outcome = result
            }
        }

        fun finish(result: Result<R>) {
            if (!terminal.compareAndSet(false, true)) return
            var finalResult = result
            try {
                labeler.close()
            } catch (closeFailure: Throwable) {
                if (finalResult.isSuccess) finalResult = Result.failure(closeFailure)
            }
            onResult(finalResult)
        }

        try {
            labeler.process(
                image = image,
                onSuccess = { value -> record(Result.success(value)) },
                onFailure = { failure -> record(Result.failure(failure)) },
                onComplete = {
                    val completed = synchronized(lock) { outcome }
                        ?: Result.failure(IllegalStateException("Image labeling completed without a result"))
                    finish(completed)
                }
            )
        } catch (setupOrCallbackFailure: Throwable) {
            // If finish already owns the terminal callback, propagate callback exceptions instead
            // of converting them into a second result. Otherwise close and report setup failure.
            if (terminal.get()) throw setupOrCallbackFailure
            finish(Result.failure(setupOrCallbackFailure))
        }
    }
}
