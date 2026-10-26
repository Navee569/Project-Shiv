package com.shiv.assistant.domain

/**
 * A real local LLM must implement this contract. The existing deterministic parser
 * is NOT a neural model. Cloud intelligence remains implemented separately.
 */
interface Brain {
    val modelId: String?
    suspend fun load(modelPath: String): BrainResult
    suspend fun infer(prompt: String, maxTokens: Int): BrainResult
    suspend fun cancel()
    suspend fun unload()
}
sealed class BrainResult {
    data class Text(val value: String): BrainResult()
    data class Unavailable(val reason: String): BrainResult()
    data class Failed(val reason: String): BrainResult()
}
class NoModelBrain : Brain {
    override val modelId: String? = null
    override suspend fun load(modelPath: String) =
        BrainResult.Unavailable("No verified Android neural runtime is installed")
    override suspend fun infer(prompt: String, maxTokens: Int) =
        BrainResult.Unavailable("Import a supported local model before requesting neural inference")
    override suspend fun cancel() {}
    override suspend fun unload() {}
}
