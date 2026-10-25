package com.shiv.assistant.ai

import android.content.Context
import android.os.SystemClock
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Conversation
import com.shiv.assistant.domain.IntelligenceMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.io.File
import java.io.Closeable
import java.util.concurrent.Executors

data class BrainStatus(
    val ready: Boolean, val modelName: String?, val queueLength: Int,
    val lastLoadMs: Long? = null, val lastReplyMs: Long? = null, val error: String? = null
)
interface Brain {
    suspend fun load(model: File)
    suspend fun infer(prompt: String): String
    suspend fun unload()
    fun status(): BrainStatus
}

/**
 * LiteRT-LM 0.18.0 real inference: single-owner executor, no UI thread work,
 * no model weights in assets; failures propagate rather than inventing a reply.
 */
class LiteRtBrain(private val context: Context) : Brain, Closeable {
    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    @Volatile private var lastLoad: Long? = null
    @Volatile private var lastReply: Long? = null
    @Volatile private var model: String? = null
    @Volatile private var error: String? = null

    override suspend fun load(model: File) = withContext(dispatcher) {
        require(model.isFile && model.length() > 1024) { "Import a real LiteRT model before loading" }
        if (this@LiteRtBrain.model == model.absolutePath && engine != null) return@withContext
        release()
        val started = SystemClock.elapsedRealtime()
        val candidate = Engine(EngineConfig(modelPath = model.absolutePath, cacheDir = context.cacheDir.absolutePath))
        try {
            candidate.initialize()
            val session = candidate.createConversation()
            engine = candidate
            conversation = session
            this@LiteRtBrain.model = model.absolutePath
            lastLoad = SystemClock.elapsedRealtime() - started
            error = null
        } catch (e: Exception) {
            // Failed initialize must NOT be followed by close on this candidate.
            error = e.message ?: "Model initialization failed"
            throw e
        }
    }
    override suspend fun infer(prompt: String): String = withContext(dispatcher) {
        require(prompt.length <= 20_000)
        val session = conversation ?: throw IllegalStateException("No local model loaded")
        val started = SystemClock.elapsedRealtime()
        try {
            val output = StringBuilder()
            session.sendMessageAsync(prompt).collect { fragment ->
                if (output.length < 12_000) output.append(fragment.toString().take(12_000 - output.length))
            }
            lastReply = SystemClock.elapsedRealtime() - started
            error = null
            output.toString().ifBlank { throw IllegalStateException("Model returned empty text") }
        } catch (e: Exception) {
            error = e.message ?: "Inference failed"
            throw e
        }
    }
    override suspend fun unload(): Unit = withContext(dispatcher) { release() }
    private fun release() {
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
        model = null
    }
    override fun status(): BrainStatus = BrainStatus(
        ready = engine != null, modelName = model?.substringAfterLast('/'),
        queueLength = 0, lastLoadMs = lastLoad, lastReplyMs = lastReply, error = error
    )
    override fun close() {
        // Close on owner dispatcher before shutting it down.
        runBlocking { unload() }
        dispatcher.close()
        executor.shutdown()
    }
}

enum class InferencePriority(val value: Int) { BACKGROUND(1), INTERACTIVE(5), URGENT(10) }
data class InferenceRequest(val id: String, val prompt: String,
    val priority: InferencePriority, val createdAt: Long = System.currentTimeMillis())

/**
 * Single active inference; no silent dropping of important requests.
 * Priority queue is bounded and errors are returned to the caller.
 */
class InferenceQueue(private val brain: Brain, private val maxSize: Int = 64) {
    private data class Pending(val request: InferenceRequest, val result: CompletableDeferred<Result<String>>)
    private val lock = Any()
    private val waiting = java.util.PriorityQueue<Pending>(
        compareByDescending<Pending> { it.request.priority.value }.thenBy { it.request.createdAt })
    private val signal = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    init { workerScope.launch { for (ignored in signal) drain() } }
    suspend fun submit(request: InferenceRequest): String {
        val promise = CompletableDeferred<Result<String>>()
        synchronized(lock) {
            if (waiting.size >= maxSize) {
                throw IllegalStateException("Inference queue full; request not discarded")
            }
            waiting.add(Pending(request, promise))
        }
        signal.trySend(Unit)
        return promise.await().getOrThrow()
    }
    private suspend fun drain() {
        while (true) {
            val next = synchronized(lock) { waiting.poll() } ?: break
            if (!next.result.isActive) continue
            try { next.result.complete(Result.success(brain.infer(next.request.prompt))) }
            catch (e: Exception) { next.result.complete(Result.failure(e)) }
        }
    }
    fun pendingCount() = synchronized(lock) { waiting.size }
    fun cancelWaiting(id: String): Boolean = synchronized(lock) {
        val target = waiting.firstOrNull { it.request.id == id } ?: return@synchronized false
        waiting.remove(target)
        target.result.cancel(CancellationException("Request cancelled"))
        true
    }
    fun shutdown() {
        workerScope.cancel()
        synchronized(lock) { waiting.forEach { it.result.cancel() }; waiting.clear() }
        signal.close()
    }
}

class ShivBrainHub(context: Context) {
    val router: Brain = LiteRtBrain(context)
    val conversation: Brain = LiteRtBrain(context)
    // All inference, from either layer, passes through exactly one queue.
    private val selected = object : Brain {
        override suspend fun load(model: File) {
            throw UnsupportedOperationException("Select the model through the model manager")
        }
        override suspend fun infer(prompt: String): String = when {
            conversation.status().ready -> conversation.infer(prompt)
            router.status().ready -> router.infer(prompt)
            else -> throw IllegalStateException("No local model loaded")
        }
        override suspend fun unload() {
            conversation.unload()
            router.unload()
        }
        override fun status() = conversation.status()
    }
    val inference = InferenceQueue(selected)
    suspend fun infer(prompt: String): String =
        inference.submit(InferenceRequest(java.util.UUID.randomUUID().toString(),
            prompt, InferencePriority.INTERACTIVE))
}
