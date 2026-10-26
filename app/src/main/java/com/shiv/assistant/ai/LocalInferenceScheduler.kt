package com.shiv.assistant.ai

import com.shiv.assistant.domain.Brain
import com.shiv.assistant.domain.BrainResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.PriorityQueue

enum class InferenceUrgency(val weight: Int) {
    BACKGROUND(10), CONVERSATION(50), URGENT(100)
}

/**
 * One active local request; bounded priority queue for pending work.
 * Interactive requests outrank background requests once the current inference ends.
 * No Android UI thread access, no hidden network or authority-granting behavior.
 */
class LocalInferenceScheduler(private val brain: Brain, private val scope: CoroutineScope) {
    private data class Ticket(
        val sequence: Long,
        val urgency: InferenceUrgency,
        val prompt: String,
        val maxTokens: Int,
        val reply: CompletableDeferred<BrainResult>
    )
    private val gate = Mutex()
    private val pending = PriorityQueue<Ticket>(
        compareByDescending<Ticket> { it.urgency.weight }.thenBy { it.sequence }
    )
    private var nextSequence = 0L
    private var worker: Job? = null
    private var closed = false
    @Volatile var waitingCount = 0
        private set

    suspend fun infer(
        prompt: String,
        maxTokens: Int = 256,
        urgency: InferenceUrgency = InferenceUrgency.CONVERSATION
    ): BrainResult {
        if (prompt.isBlank() || maxTokens !in 1..2048)
            return BrainResult.Failed("Invalid inference request")
        val reply = CompletableDeferred<BrainResult>()
        gate.withLock {
            if (closed) return BrainResult.Unavailable("Inference scheduler closed")
            if (pending.size >= 32)
                return BrainResult.Failed("Local inference queue is full")
            pending.add(Ticket(nextSequence++, urgency, prompt.take(12000), maxTokens, reply))
            waitingCount = pending.size
            if (worker?.isActive != true) worker = scope.launch { drain() }
        }
        return try { reply.await() }
        catch (e: CancellationException) {
            reply.cancel()
            throw e
        }
    }

    private suspend fun drain() {
        while (true) {
            val ticket = gate.withLock {
                while (pending.isNotEmpty() && pending.peek().reply.isCancelled) pending.poll()
                val next = pending.poll()
                waitingCount = pending.size
                if (next == null) worker = null
                next
            } ?: return
            if (ticket.reply.isCancelled) continue
            val answer = try { brain.infer(ticket.prompt, ticket.maxTokens) }
            catch (e: CancellationException) {
                ticket.reply.cancel()
                // A canceled *inference* is recoverable; abandoning the drain here
                // would strand all higher-priority pending tickets indefinitely.
                // A canceled scheduler scope, however, must still shut down.
                if (!currentCoroutineContext().isActive) throw e
                continue
            } catch (e: Exception) {
                BrainResult.Failed("Local inference failure: " + (e.message ?: "unknown error"))
            }
            ticket.reply.complete(answer)
        }
    }

    suspend fun interrupt() = brain.cancel()

    suspend fun close() {
        val job = gate.withLock {
            closed = true
            while (pending.isNotEmpty()) {
                pending.poll().reply.complete(BrainResult.Unavailable("Scheduler stopped"))
            }
            waitingCount = 0
            worker
        }
        job?.cancelAndJoin()
        brain.unload()
    }
}
