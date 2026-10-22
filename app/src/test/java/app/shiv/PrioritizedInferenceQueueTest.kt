package app.shiv

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.RejectedExecutionException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.coroutines.TimeoutCancellationException
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
        try {
            withTimeout(2_000) { while (queue.queuedCount == 0) yield() }
            queued.cancelAndJoin()
            releaseActive.complete(Unit)
            assertEquals(1, withTimeout(2_000) { active.await() })
            assertTrue(!queuedRan)
        } finally {
            releaseActive.complete(Unit)
            queue.close()
        }
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

    @Test fun concurrentSubmitCloseRaceAlwaysTerminates() = runBlocking {
        val queue = PrioritizedInferenceQueue()
        val start = CompletableDeferred<Unit>()
        val requests = coroutineScope {
            (1..64).map {
                async {
                    start.await()
                    runCatching { withTimeout(2_000) { queue.submit(1, 60_000, { -1 }) { 1 } } }
                }
            }
        }
        start.complete(Unit)
        queue.close()
        val outcomes = withTimeout(3_000) { requests.awaitAll() }
        assertEquals(64, outcomes.size)
        assertTrue("A submission hung until its timeout", outcomes.none { it.exceptionOrNull() is TimeoutCancellationException })
    }

    @Test fun concurrentSubmissionsRespectQueueCapacity() {
        runBlocking {
        val queue = PrioritizedInferenceQueue()
        val activeStarted = CompletableDeferred<Unit>()
        val releaseActive = CompletableDeferred<Unit>()
        val active = async { queue.submit(100, 60_000, { -1 }) { activeStarted.complete(Unit); releaseActive.await(); 1 } }
        activeStarted.await()
        val gate = CompletableDeferred<Unit>()
        val outcomes = coroutineScope {
            (1..64).map {
                async {
                    gate.await()
                    runCatching { queue.submit(1, 60_000, { -1 }) { 2 } }
                }
            }
        }
        gate.complete(Unit)
        val submissions = withTimeout(2_000) { outcomes.awaitAll() }
        val rejected = submissions.count { it.exceptionOrNull() is RejectedExecutionException }
        assertTrue("Expected capacity rejection; outcomes=$submissions", rejected > 0)
        queue.close()
        releaseActive.complete(Unit)
        runCatching { withTimeout(2_000) { active.await() } }
        Unit
        }
    }


    @Test fun callerCancellationCancelsAnAlreadyRunningTask() = runBlocking {
        val queue = PrioritizedInferenceQueue()
        val started = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val caller = async {
            queue.submit(10, 60_000, { -1 }) {
                started.complete(Unit)
                try { CompletableDeferred<Unit>().await() }
                finally { finished.complete(Unit) }
                7
            }
        }
        try {
            withTimeout(2_000) { started.await() }
            caller.cancel()
            caller.cancelAndJoin()
            withTimeout(2_000) { finished.await() }
            assertEquals(9, withTimeout(2_000) { queue.submit(1, 60_000, { -1 }) { 9 } })
        } finally {
            queue.close()
        }
    }

    @Test fun throwingStaleValueFailsCallerButLeavesConsumerUsable() = runBlocking {
        val queue = PrioritizedInferenceQueue()
        try {
            val failed = runCatching {
                queue.submit(10, -1, { throw IllegalStateException("stale failed") }) { 1 }
            }
            assertTrue(failed.exceptionOrNull() is IllegalStateException)
            assertEquals(5, withTimeout(2_000) { queue.submit(1, 60_000, { -1 }) { 5 } })
        } finally {
            queue.close()
        }
    }

    @Test fun closeIsIdempotentAndRejectsLaterSubmissions() = runBlocking {
        val queue = PrioritizedInferenceQueue()
        queue.close()
        queue.close()
        assertTrue(runCatching { queue.submit(1, 1000, { 0 }) { 1 } }.isFailure)
    }


    @Test fun closeMakesEveryQueuedCallerTerminal() = runBlocking {
        val queue = PrioritizedInferenceQueue()
        val activeStarted = CompletableDeferred<Unit>()
        val releaseActive = CompletableDeferred<Unit>()
        val active = async {
            runCatching { queue.submit(100, 60_000, { -1 }) {
                activeStarted.complete(Unit)
                releaseActive.await()
                1
            } }
        }
        val pending = mutableListOf<kotlinx.coroutines.Deferred<Result<Int>>>()
        try {
            withTimeout(2_000) { activeStarted.await() }
            repeat(8) {
                pending += async { runCatching { queue.submit(1, 60_000, { -1 }) { 2 } } }
            }
            withTimeout(2_000) { while (queue.queuedCount < 8) yield() }
            queue.close()
            val results = withTimeout(2_000) { pending.awaitAll() }
            assertEquals(8, results.size)
            assertTrue("A queued caller remained active after close", pending.none { it.isActive })
            withTimeout(2_000) { active.await() }
        } finally {
            releaseActive.complete(Unit)
            queue.close()
        }
    }

}
