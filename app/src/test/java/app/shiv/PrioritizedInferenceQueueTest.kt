package app.shiv

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrioritizedInferenceQueueTest {
    @Test fun cancelledQueuedRequestIsNotExecuted() = runBlocking {
        val queue = PrioritizedInferenceQueue()
        val activeStarted = CompletableDeferred<Unit>()
        val releaseActive = CompletableDeferred<Unit>()
        val active = async { queue.submit(100, 60_000, { -1 }) { activeStarted.complete(Unit); releaseActive.await(); 1 } }
        activeStarted.await()
        var queuedRan = false
        val queued = async { queue.submit(10, 60_000, { -1 }) { queuedRan = true; 2 } }
        delay(30); queued.cancelAndJoin(); releaseActive.complete(Unit)
        assertEquals(1, withTimeout(2_000) { active.await() })
        delay(80); assertTrue(!queuedRan); queue.close()
    }
    @Test fun cancellationExceptionDoesNotKillConsumer() = runBlocking {
        val queue = PrioritizedInferenceQueue()
        val first = async { runCatching { queue.submit(100, 60_000, { -1 }) { throw kotlinx.coroutines.CancellationException("test") } }.isFailure }
        assertTrue(withTimeout(2_000) { first.await() })
        assertEquals("alive", withTimeout(2_000) { queue.submit(10, 60_000, { "stale" }) { "alive" } })
        queue.close()
    }
    @Test fun closedQueueRejectsFurtherRequests() = runBlocking {
        val queue = PrioritizedInferenceQueue(); queue.close()
        assertTrue(runCatching { runBlocking { queue.submit(1, 1000, { 0 }) { 1 } } }.isFailure)
    }
}
