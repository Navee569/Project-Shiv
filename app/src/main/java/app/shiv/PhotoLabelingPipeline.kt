package app.shiv

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeler
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

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
 * Waits for a task after listener registration fails without blocking the caller.
 * Results are dispatched on the main executor to match the normal Task listener contract.
 * If either executor rejects work, completion is not signalled: the pipeline must retain
 * image ownership rather than closing an image that may still be in use.
 */
internal object ListenerRegistrationFallback {
    fun schedule(
        awaitTask: () -> Unit,
        registrationFailure: Throwable,
        waiter: Executor,
        callbackExecutor: Executor,
        completionExecutor: Executor = callbackExecutor,
        onFailure: (Throwable) -> Unit,
        onComplete: () -> Unit,
        onSchedulingFailure: (Throwable) -> Unit = {}
    ): Boolean {
        return try {
            waiter.execute {
                val terminalFailure = try {
                    awaitTask()
                    registrationFailure
                } catch (taskFailure: Throwable) {
                    if (taskFailure !== registrationFailure) taskFailure.addSuppressed(registrationFailure)
                    taskFailure
                }
                val completion = Runnable {
                    try {
                        onFailure(terminalFailure)
                    } finally {
                        onComplete()
                    }
                }
                try {
                    callbackExecutor.execute(completion)
                } catch (callbackRejected: RejectedExecutionException) {
                    try {
                        completionExecutor.execute(completion)
                    } catch (completionRejected: RejectedExecutionException) {
                        completionRejected.addSuppressed(callbackRejected)
                        onSchedulingFailure(completionRejected)
                    }
                }
            }
            true
        } catch (rejected: RejectedExecutionException) {
            onSchedulingFailure(rejected)
            false
        }
    }
}

internal object PhotoLabelingExecutors {
    val waiter: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "shiv-mlkit-fallback").apply { isDaemon = true }
    }
    val mainCallback: Executor = Executor { command ->
        if (!Handler(Looper.getMainLooper()).post(command)) {
            throw RejectedExecutionException("Main-thread callback could not be scheduled")
        }
    }
    val mainCompletion: Executor = Executor { command ->
        if (!Handler(Looper.getMainLooper()).post(command)) {
            throw RejectedExecutionException("Main-thread completion could not be scheduled")
        }
    }
}

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
            ListenerRegistrationFallback.schedule(
                awaitTask = { InterruptSafeAwait.await(awaitOnce = { Tasks.await(task) }); Unit },
                registrationFailure = registrationFailure,
                waiter = PhotoLabelingExecutors.waiter,
                callbackExecutor = PhotoLabelingExecutors.mainCallback,
                completionExecutor = PhotoLabelingExecutors.mainCompletion,
                onFailure = onFailure,
                onComplete = onComplete,
                onSchedulingFailure = {
                    Log.e("ShivPhotoLabeler", "Fallback scheduling failed; retaining image ownership", it)
                }
            )
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
