package com.shiv.assistant.ai

import com.shiv.assistant.domain.Brain
import com.shiv.assistant.domain.BrainResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections

class LocalInferenceSchedulerTest {
    private class FakeBrain : Brain {
        val started = CompletableDeferred<Unit>()
        val unblock = CompletableDeferred<Unit>()
        val order = Collections.synchronizedList(mutableListOf<String>())
        override val modelId: String? = "fake"
        override suspend fun load(modelPath: String) = BrainResult.Text("loaded")
        override suspend fun infer(prompt: String, maxTokens: Int): BrainResult {
            order.add(prompt)
            if (prompt == "block") {
                started.complete(Unit)
                unblock.await()
            }
            return BrainResult.Text(prompt)
        }
        override suspend fun cancel() {}
        override suspend fun unload() {}
    }
    @Test fun urgentRequestPrecedesPendingBackgroundWork() = runBlocking {
        val brain = FakeBrain()
        val scheduler = LocalInferenceScheduler(brain, this)
        val active = async(Dispatchers.Default) { scheduler.infer("block") }
        brain.started.await()
        val low = async(Dispatchers.Default) {
            scheduler.infer("background", urgency = InferenceUrgency.BACKGROUND)
        }
        withTimeout(2000) { while (scheduler.waitingCount < 1) delay(2) }
        val urgent = async(Dispatchers.Default) {
            scheduler.infer("urgent", urgency = InferenceUrgency.URGENT)
        }
        withTimeout(2000) { while (scheduler.waitingCount < 2) delay(2) }
        brain.unblock.complete(Unit)
        active.await(); low.await(); urgent.await()
        assertEquals(listOf("block", "urgent", "background"), brain.order.toList())
        scheduler.close()
    }
    @Test fun cancelledActiveInferenceDoesNotStrandPendingWork() = runBlocking {
        val brain = FakeBrain()
        val scheduler = LocalInferenceScheduler(brain, this)
        val active = async(Dispatchers.Default) {
            runCatching { scheduler.infer("block") }.exceptionOrNull()
        }
        withTimeout(2000) { brain.started.await() }
        val queued = async(Dispatchers.Default) {
            scheduler.infer("after", urgency = InferenceUrgency.URGENT)
        }
        withTimeout(2000) { while (scheduler.waitingCount < 1) delay(2) }
        // Mimics an inference cancelled by the native runtime while the
        // scheduler coroutine itself is still alive.
        brain.unblock.cancel()
        assertTrue(active.await() is kotlinx.coroutines.CancellationException)
        assertEquals(BrainResult.Text("after"), withTimeout(2000) { queued.await() })
        assertEquals(listOf("block", "after"), brain.order.toList())
        scheduler.close()
    }

    @Test fun invalidInputNeverReachesBrain() = runBlocking {
        val brain = FakeBrain()
        val scheduler = LocalInferenceScheduler(brain, this)
        assertTrue(scheduler.infer("").let { it is BrainResult.Failed })
        assertTrue(scheduler.infer("hello", maxTokens = 0).let { it is BrainResult.Failed })
        assertEquals(0, brain.order.size)
        scheduler.close()
    }
}
