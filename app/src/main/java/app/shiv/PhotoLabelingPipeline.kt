package app.shiv

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeler
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

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

/** Indicates that waiting ended without proving task terminality; ownership must remain retained. */
internal class TaskTerminalityUnresolvedException(cause: Throwable) :
    IllegalStateException("ML Kit task terminality could not be established", cause)

/**
 * Runs the blocking Tasks.await fallback away from the caller. Successful results stay successful
 * even if listener registration failed. If capacity is exhausted, the caller retains image
 * ownership because task terminality has not been established.
 */
internal object ListenerRegistrationFallback {
    fun <R> schedule(
        awaitTask: () -> R,
        registrationFailure: Throwable,
        waiter: Executor,
        onSuccess: (R) -> Unit,
        onFailure: (Throwable) -> Unit,
        onComplete: () -> Unit,
        onSchedulingFailure: (Throwable) -> Unit = {}
    ): Boolean {
        val waitAndDeliver = Runnable {
            val outcome: Result<R> = try {
                Result.success(awaitTask())
            } catch (unresolved: TaskTerminalityUnresolvedException) {
                reportSafely(onSchedulingFailure, unresolved)
                return@Runnable
            } catch (taskFailure: Throwable) {
                if (taskFailure !== registrationFailure) {
                    try {
                        taskFailure.addSuppressed(registrationFailure)
                    } catch (_: Throwable) {
                        // Preserve the actual task failure even if diagnostics cannot be attached.
                    }
                }
                Result.failure(taskFailure)
            }

            var callbackFailure: Throwable? = null
            try {
                outcome.fold(onSuccess, onFailure)
            } catch (failure: Throwable) {
                callbackFailure = failure
            } finally {
                try {
                    onComplete()
                } catch (completionFailure: Throwable) {
                    val primary = callbackFailure
                    if (primary == null) callbackFailure = completionFailure
                    else if (primary !== completionFailure) {
                        try { primary.addSuppressed(completionFailure) } catch (_: Throwable) { }
                    }
                }
            }
            callbackFailure?.let { reportSafely(onSchedulingFailure, it) }
        }

        return try {
            waiter.execute(waitAndDeliver)
            true
        } catch (rejected: RejectedExecutionException) {
            reportSafely(onSchedulingFailure, rejected)
            false
        }
    }

    private fun reportSafely(report: (Throwable) -> Unit, failure: Throwable) {
        try { report(failure) } catch (_: Throwable) {
            // Diagnostics must never change task or image ownership.
        }
    }
}

internal object PhotoLabelingExecutors {
    const val DEFAULT_WAITER_THREADS = 2
    const val DEFAULT_WAITER_QUEUE_CAPACITY = 8
    const val TASK_AWAIT_TIMEOUT_MILLIS = 120_000L

    fun createWaiterExecutor(
        workerCount: Int = DEFAULT_WAITER_THREADS,
        queueCapacity: Int = DEFAULT_WAITER_QUEUE_CAPACITY,
        threadFactory: ThreadFactory = ThreadFactory { runnable ->
            Thread(runnable, "shiv-mlkit-fallback").apply { isDaemon = true }
        }
    ): ThreadPoolExecutor {
        require(workerCount > 0) { "workerCount must be positive" }
        require(queueCapacity > 0) { "queueCapacity must be positive" }
        return ThreadPoolExecutor(
            workerCount,
            workerCount,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(queueCapacity),
            threadFactory,
            ThreadPoolExecutor.AbortPolicy()
        )
    }

    val waiter: Executor = createWaiterExecutor()

    val mainCallback: Executor = Executor { command ->
        if (!Handler(Looper.getMainLooper()).post(command)) {
            throw RejectedExecutionException("Main-thread callback could not be scheduled")
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
                awaitTask = {
                    try {
                        InterruptSafeAwait.await {
                            Tasks.await(task, PhotoLabelingExecutors.TASK_AWAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                        }
                    } catch (timeout: TimeoutException) {
                        throw TaskTerminalityUnresolvedException(timeout)
                    } catch (awaitFailure: Throwable) {
                        throw task.exception ?: awaitFailure
                    }
                },
                registrationFailure = registrationFailure,
                waiter = PhotoLabelingExecutors.waiter,
                onSuccess = onSuccess,
                onFailure = onFailure,
                onComplete = onComplete,
                onSchedulingFailure = {
                    Log.e("ShivPhotoLabeler", "Fallback could not establish task terminality; image ownership is retained", it)
                }
            )
        }
    }

    override fun close() = delegate.close()
}

/**
 * Cleanup is committed before result delivery is scheduled. A rejected UI dispatcher can therefore
 * lose UI notification, but cannot strand a terminal image or cause callbacks on the waiter thread.
 */
internal object PhotoLabelingPipeline {
    private val directExecutor = Executor { command -> command.run() }

    fun <I, R> run(
        image: I,
        labeler: PhotoLabeler<I, R>,
        callbackExecutor: Executor = directExecutor,
        onImageConsumed: () -> Unit = {},
        onDeliveryFailure: (Throwable) -> Unit = {},
        onResult: (Result<R>) -> Unit
    ) {
        val lock = Any()
        var outcome: Result<R>? = null
        var terminal = false

        fun record(result: Result<R>) {
            synchronized(lock) { if (!terminal && outcome == null) outcome = result }
        }

        fun addFailure(current: Result<R>, additional: Throwable): Result<R> {
            val primary = current.exceptionOrNull()
            if (primary == null) return Result.failure(additional)
            if (primary !== additional) {
                try { primary.addSuppressed(additional) } catch (_: Throwable) { }
            }
            return current
        }

        fun closeAndDeliver(initialResult: Result<R>) {
            var finalResult = initialResult
            try {
                labeler.close()
            } catch (closeFailure: Throwable) {
                finalResult = addFailure(finalResult, closeFailure)
            }
            try {
                onImageConsumed()
            } catch (cleanupFailure: Throwable) {
                finalResult = addFailure(finalResult, cleanupFailure)
            }

            val stableResult = finalResult
            val started = AtomicBoolean(false)
            try {
                callbackExecutor.execute {
                    started.set(true)
                    onResult(stableResult)
                }
            } catch (dispatchFailure: RejectedExecutionException) {
                if (started.get()) throw dispatchFailure
                try { onDeliveryFailure(dispatchFailure) } catch (_: Throwable) {
                    // Reporting a delivery failure must not undo completed cleanup.
                }
            }
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
