package app.shiv.domain.inference

import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PrioritizedInferenceQueueTest {
    @Test
    fun queuedTasksRunByPriorityAfterCurrentInferenceFinishes() = runBlocking {
        val queue = PrioritizedInferenceQueue(dispatcher = Dispatchers.Default)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val order = Collections.synchronizedList(mutableListOf<String>())
        val first = async {
            queue.submit(1, 10_000, { "stale-first" }) {
                order += "first"
                firstStarted.complete(Unit)
                releaseFirst.await()
                "first"
            }
        }
        firstStarted.await()
        val ordinary = async { queue.submit(50, 10_000, { "stale" }) { order += "ordinary"; "ordinary" } }
        val urgent = async { queue.submit(100, 10_000, { "stale" }) { order += "urgent"; "urgent" } }
        awaitQueued(queue, 2)

        releaseFirst.complete(Unit)
        assertEquals("first", first.await())
        assertEquals("urgent", urgent.await())
        assertEquals("ordinary", ordinary.await())
        assertEquals(listOf("first", "urgent", "ordinary"), order)
    }

    @Test
    fun cancellingWaitingCallerRemovesItsQueuedInference() = runBlocking {
        val queue = PrioritizedInferenceQueue(dispatcher = Dispatchers.Default)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val cancelledBlockRan = CompletableDeferred<Unit>()
        val first = async {
            queue.submit(1, 10_000, { "stale" }) {
                firstStarted.complete(Unit)
                releaseFirst.await()
                "first"
            }
        }
        firstStarted.await()
        val cancelled = async {
            queue.submit(50, 10_000, { "stale" }) {
                cancelledBlockRan.complete(Unit)
                "cancelled"
            }
        }
        awaitQueued(queue, 1)
        cancelled.cancelAndJoin()
        awaitQueued(queue, 0)

        releaseFirst.complete(Unit)
        first.await()
        delay(20)
        assertFalse(cancelledBlockRan.isCompleted)
    }

    @Test
    fun staleQueuedInferenceUsesFallbackWithoutRunningBlock() = runBlocking {
        val queue = PrioritizedInferenceQueue(dispatcher = Dispatchers.Default)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val staleBlockRan = CompletableDeferred<Unit>()
        val first = async {
            queue.submit(1, 10_000, { "stale-first" }) {
                firstStarted.complete(Unit)
                releaseFirst.await()
                "first"
            }
        }
        firstStarted.await()
        val stale = async {
            queue.submit(50, 1, { "stale-fallback" }) {
                staleBlockRan.complete(Unit)
                "unexpected"
            }
        }
        awaitQueued(queue, 1)
        delay(20)
        releaseFirst.complete(Unit)

        first.await()
        assertEquals("stale-fallback", stale.await())
        assertFalse(staleBlockRan.isCompleted)
    }

    private suspend fun awaitQueued(queue: InferenceQueue, expected: Int) {
        withTimeout(2_000) {
            while (queue.queuedCount != expected) delay(1)
        }
    }
}
