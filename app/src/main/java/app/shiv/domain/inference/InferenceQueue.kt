package app.shiv.domain.inference

import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Platform-free contract for serial, prioritized inference work. */
interface InferenceQueue {
    val queuedCount: Int
    val isRunning: Boolean

    suspend fun <T> submit(
        priority: Int,
        staleAfterMillis: Long,
        staleValue: () -> T,
        block: suspend () -> T
    ): T
}

/** One consumer executes tasks by priority and submission order; expired or cancelled tasks do no work. */
internal class PrioritizedInferenceQueue(
    private val onChanged: () -> Unit = {},
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : InferenceQueue {
    private data class Task(
        val priority: Int,
        val sequence: Long,
        val createdAt: Long,
        val staleAfterMillis: Long,
        val execute: suspend () -> Unit
    )

    private val sequence = AtomicLong()
    private val tasks = PriorityBlockingQueue<Task>(11, compareByDescending<Task> { it.priority }.thenBy { it.sequence })
    private val wake = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    override var isRunning: Boolean = false
        private set

    override val queuedCount: Int get() = tasks.size

    init {
        CoroutineScope(SupervisorJob() + dispatcher).launch {
            for (ignored in wake) {
                while (true) {
                    val task = tasks.poll() ?: break
                    isRunning = true
                    onChanged()
                    try {
                        task.execute()
                    } finally {
                        isRunning = false
                        onChanged()
                    }
                }
            }
        }
    }

    override suspend fun <T> submit(
        priority: Int,
        staleAfterMillis: Long,
        staleValue: () -> T,
        block: suspend () -> T
    ): T {
        require(staleAfterMillis >= 0) { "staleAfterMillis must not be negative" }
        val result = CompletableDeferred<T>()
        val createdAt = System.currentTimeMillis()
        val task = Task(priority, sequence.getAndIncrement(), createdAt, staleAfterMillis) {
            if (result.isCancelled) return@Task
            if (System.currentTimeMillis() - createdAt > staleAfterMillis) {
                result.complete(staleValue())
            } else {
                try {
                    result.complete(block())
                } catch (failure: Throwable) {
                    result.completeExceptionally(failure)
                    if (failure is CancellationException) throw failure
                }
            }
        }
        tasks.add(task)
        onChanged()
        wake.trySend(Unit)
        return try {
            result.await()
        } catch (cancelled: CancellationException) {
            result.cancel(cancelled)
            tasks.remove(task)
            onChanged()
            throw cancelled
        }
    }
}
