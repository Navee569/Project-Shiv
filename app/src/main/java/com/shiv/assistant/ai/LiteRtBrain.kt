package com.shiv.assistant.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.shiv.assistant.domain.Brain
import com.shiv.assistant.domain.BrainResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Real on-device LiteRT-LM inference, no network, no cloud endpoint.
 * One engine and one active inference at a time. Any unsupported model fails explicitly.
 * The caller must provide a file-system path in app-private storage.
 */
class LiteRtBrain(private val context: Context) : Brain {
    private val gate = Mutex()
    private var engine: Engine? = null
    @Volatile private var conversation: Conversation? = null
    @Volatile private var loadedPath: String? = null
    override val modelId: String? get() = loadedPath

    override suspend fun load(modelPath: String): BrainResult = withContext(Dispatchers.IO) {
        gate.withLock {
            if (loadedPath == modelPath && conversation != null)
                return@withLock BrainResult.Text("Model already loaded")
            closeInternal()
            var initialized = false
            var candidate: Engine? = null
            try {
                val config = EngineConfig(modelPath = modelPath,
                    backend = Backend.CPU(), cacheDir = context.cacheDir.absolutePath)
                candidate = Engine(config)
                candidate.initialize()
                initialized = true
                val dialog = candidate.createConversation(ConversationConfig(
                    systemInstruction = Contents.of(
                        "You are Shiv, the owner's private Android assistant. " +
                        "Do not claim to have executed an Android action. " +
                        "Action execution is permitted only by the app's deterministic policy."
                    )
                ))
                engine = candidate
                conversation = dialog
                loadedPath = modelPath
                BrainResult.Text("Local model initialized on CPU")
            } catch (e: Exception) {
                if (initialized) runCatching { candidate?.close() }
                engine = null
                conversation = null
                loadedPath = null
                BrainResult.Failed("Local runtime initialization failed: " +
                    (e.message ?: e.javaClass.simpleName))
            }
        }
    }

    override suspend fun infer(prompt: String, maxTokens: Int): BrainResult = withContext(Dispatchers.IO) {
        gate.withLock {
            val chat = conversation ?: return@withLock BrainResult.Unavailable("No local neural model loaded")
            if (prompt.isBlank()) return@withLock BrainResult.Failed("Empty prompt")
            if (maxTokens !in 1..2048) return@withLock BrainResult.Failed("Invalid output budget")
            val output = StringBuilder()
            try {
                chat.sendMessageAsync(prompt.take(12000)).collect { part ->
                    // Bound the UI-visible result. LiteRT owns the actual generation lifecycle.
                    val chunk = part.toString()
                    if (output.length < maxTokens * 8) output.append(chunk.take(maxTokens * 8 - output.length))
                }
                BrainResult.Text(output.toString().ifBlank { "Model returned an empty response" })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BrainResult.Failed("Local inference failed: " + (e.message ?: e.javaClass.simpleName))
            }
        }
    }

    override suspend fun cancel() {
        withContext(Dispatchers.IO) { runCatching { conversation?.cancelProcess() } }
    }
    override suspend fun unload() = withContext(Dispatchers.IO) {
        cancel()
        gate.withLock { closeInternal() }
    }
    private fun closeInternal() {
        runCatching { conversation?.close() }
        conversation = null
        runCatching { engine?.close() }
        engine = null
        loadedPath = null
    }
}
