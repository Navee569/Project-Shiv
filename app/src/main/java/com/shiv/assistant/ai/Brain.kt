package com.shiv.assistant.ai

import android.content.Context
import android.os.SystemClock
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Conversation
import com.shiv.assistant.domain.IntelligenceMode
import com.shiv.assistant.data.LocalModelComputeLedger
import com.shiv.assistant.data.ShivVault
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.io.File
import java.io.Closeable
import java.util.concurrent.Executors
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class BrainStatus(
    val ready: Boolean, val modelName: String?, val queueLength: Int,
    val lastLoadMs: Long? = null, val lastReplyMs: Long? = null, val error: String? = null,
    val firstFragmentMs: Long? = null, val lastOutputCharacters: Int? = null
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
    @Volatile private var firstFragment: Long? = null
    @Volatile private var outputCharacters: Int? = null

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
            firstFragment = null
            session.sendMessageAsync(prompt).collect { fragment ->
                if (firstFragment == null) firstFragment = SystemClock.elapsedRealtime() - started
                if (output.length < 12_000) output.append(fragment.toString().take(12_000 - output.length))
            }
            lastReply = SystemClock.elapsedRealtime() - started
            outputCharacters = output.length
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
        queueLength = 0, lastLoadMs = lastLoad, lastReplyMs = lastReply, error = error,
        firstFragmentMs = firstFragment, lastOutputCharacters = outputCharacters
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
        require(request.prompt.isNotBlank() && request.prompt.length <= 20_000) {
            "Invalid or oversized inference request"
        }
        val promise = CompletableDeferred<Result<String>>()
        synchronized(lock) {
            if (waiting.size >= maxSize) {
                throw IllegalStateException("Inference queue full; request not discarded")
            }
            waiting.add(Pending(request, promise))
        }
        signal.trySend(Unit)
        return try {
            promise.await().getOrThrow()
        } catch (cancelled: CancellationException) {
            // Cancellation of a caller must never leave a queued prompt behind.
            synchronized(lock) {
                val outstanding = waiting.firstOrNull { it.result === promise }
                if (outstanding != null) waiting.remove(outstanding)
            }
            throw cancelled
        }
    }
    private suspend fun drain() {
        while (true) {
            val next = synchronized(lock) { waiting.poll() } ?: break
            if (!next.result.isActive) continue
            try {
                // Best-effort coroutine deadline. A native engine may not stop
                // computation instantaneously; do not claim hard cancellation.
                val response = withTimeout(120_000L) { brain.infer(next.request.prompt) }
                next.result.complete(Result.success(response))
            } catch (e: CancellationException) {
                next.result.complete(Result.failure(e))
                if (!workerScope.isActive) return
                // One canceled request must not strand later priority entries.
            } catch (e: Exception) {
                next.result.complete(Result.failure(e))
            }
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

/**
 * Local-only two-role coordinator. A single lifecycle mutex prevents loading or
 * releasing a model while its native inference is active. Battery, temperature
 * and RAM are rechecked for every request, not merely when loading weights.
 */
class ShivBrainHub(private val context: Context) {
    val router: Brain = LiteRtBrain(context)
    val conversation: Brain = LiteRtBrain(context)
    private val lifecycle = Mutex()
    private val computeLedger=LocalModelComputeLedger(ShivVault(context))
    private val idleScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var idleRelease: Job? = null

    private val selected = object : Brain {
        override suspend fun load(model: File): Unit =
            throw UnsupportedOperationException("Import models through Model Manager")
        override suspend fun infer(prompt: String): String {
            val governor = ModelGovernor(context)
            val heavy = governor.lease("conversation")
            val small = governor.lease("router")
            val selectedBrain:Brain
            val maxSeconds:Int
            when {
                conversation.status().ready && heavy.allowed -> {
                    selectedBrain=conversation
                    maxSeconds=heavy.maxModelSeconds
                }
                router.status().ready && small.allowed -> {
                    selectedBrain=router
                    maxSeconds=small.maxModelSeconds
                }
                conversation.status().ready && !heavy.allowed ->
                    throw IllegalStateException("Conversation model blocked: " + heavy.reason)
                else -> throw IllegalStateException(
                    "No local model admitted: "+small.reason)
            }
            val started=SystemClock.elapsedRealtime()
            try {
                // Only a best-effort coroutine limit; a native runtime may
                // finish cancellation after this window.
                return withTimeout(maxSeconds.toLong()*1000L) {
                    selectedBrain.infer(prompt)
                }
            } finally {
                computeLedger.record(SystemClock.elapsedRealtime()-started)
            }
        }
        override suspend fun unload() {
            conversation.unload()
            router.unload()
        }
        override fun status() = conversation.status()
    }

    val inference = InferenceQueue(selected)
    suspend fun load(role: String, file: File, availableRam: Long) = lifecycle.withLock {
        require(role in setOf("router","conversation"))
        idleRelease?.cancel()
        // A small router and large conversation should not coexist under pressure.
        if (role=="conversation" && availableRam<4_000_000_000L) router.unload()
        if (role=="router" && availableRam<4_000_000_000L) conversation.unload()
        val target = if (role=="router") router else conversation
        val started=SystemClock.elapsedRealtime()
        try { target.load(file) }
        finally { computeLedger.record(SystemClock.elapsedRealtime()-started) }
        armIdleRelease()
    }
    suspend fun unloadForRemoval(role: String, filename: String) = lifecycle.withLock {
        if (role=="router" && router.status().modelName==filename) router.unload()
        if (role=="conversation" && conversation.status().modelName==filename) conversation.unload()
    }
    private fun armIdleRelease() {
        idleRelease?.cancel()
        if (!conversation.status().ready) return
        idleRelease = idleScope.launch {
            delay(120_000L)
            lifecycle.withLock {
                // This is best-effort idle eviction, not a measured energy saving.
                if (conversation.status().ready) conversation.unload()
            }
        }
    }
    suspend fun infer(prompt: String): String = lifecycle.withLock {
        idleRelease?.cancel()
        try {
            inference.submit(InferenceRequest(java.util.UUID.randomUUID().toString(),
                prompt, InferencePriority.INTERACTIVE))
        } finally { armIdleRelease() }
    }
    suspend fun clearSessions() = lifecycle.withLock {
        idleRelease?.cancel()
        conversation.unload()
        router.unload()
    }
    suspend fun shutdown() = lifecycle.withLock {
        idleRelease?.cancel()
        inference.shutdown()
        conversation.unload()
        router.unload()
        idleScope.cancel()
    }
}
