package com.shiv.assistant.ai

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class InferenceQueueReliabilityTest {
    private class FakeBrain : Brain {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val executed = mutableListOf<String>()
        override suspend fun load(model:File) {}
        override suspend fun infer(prompt:String):String {
            synchronized(executed) { executed.add(prompt) }
            if(prompt=="first") {
                entered.complete(Unit)
                gate.await()
            }
            return "reply:"+prompt
        }
        override suspend fun unload() {}
        override fun status()=BrainStatus(true,"fake",0)
    }
    @Test fun canceledCallerIsRemovedAndNextRequestCompletes() = runBlocking {
        val fake=FakeBrain()
        val queue=InferenceQueue(fake)
        try {
            val first=async { queue.submit(InferenceRequest("1","first",InferencePriority.INTERACTIVE)) }
            withTimeout(3000) { fake.entered.await() }
            val abandoned=async {
                queue.submit(InferenceRequest("2","abandoned",InferencePriority.BACKGROUND))
            }
            withTimeout(3000) { while(queue.pendingCount()!=1) delay(10) }
            abandoned.cancelAndJoin()
            assertEquals(0,queue.pendingCount())
            val third=async { queue.submit(InferenceRequest("3","third",InferencePriority.URGENT)) }
            fake.gate.complete(Unit)
            assertEquals("reply:first",withTimeout(4000){first.await()})
            assertEquals("reply:third",withTimeout(4000){third.await()})
            assertEquals(listOf("first","third"),fake.executed)
        } finally { queue.shutdown() }
    }
    @Test fun invalidAndOversizedInputsFailBeforeNativeInference() = runBlocking {
        val fake=FakeBrain()
        val queue=InferenceQueue(fake)
        try {
            for(input in listOf("", "a".repeat(20_001))) {
                try {
                    queue.submit(InferenceRequest("bad",input,InferencePriority.INTERACTIVE))
                    fail("Invalid prompt must be rejected")
                } catch (_: IllegalArgumentException) {}
            }
            assertTrue(fake.executed.isEmpty())
        } finally { queue.shutdown() }
    }
    @Test fun cancelingActiveCallerStopsCooperativeInferenceAndFreesQueue() = runBlocking {
        val entered=CompletableDeferred<Unit>()
        val canceled=CompletableDeferred<Unit>()
        val fake=object:Brain {
            override suspend fun load(model:File) {}
            override suspend fun infer(prompt:String):String {
                if(prompt=="active") {
                    entered.complete(Unit)
                    try { awaitCancellation() }
                    finally { canceled.complete(Unit) }
                }
                return "reply:"+prompt
            }
            override suspend fun unload() {}
            override fun status()=BrainStatus(true,"fake",0)
        }
        val queue=InferenceQueue(fake)
        try {
            val first=async { queue.submit(InferenceRequest("active","active",InferencePriority.INTERACTIVE)) }
            withTimeout(3000) { entered.await() }
            first.cancelAndJoin()
            withTimeout(3000) { canceled.await() }
            assertEquals("reply:next",withTimeout(4000) {
                queue.submit(InferenceRequest("next","next",InferencePriority.URGENT))
            })
        } finally { queue.shutdown() }
    }
}
