package com.shiv.assistant.ai

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.shiv.assistant.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The LOCAL mode never contacts a server. HYBRID attempts local commands before
 * the explicitly consented cloud model; a cloud outage does not erase local functionality.
 */
class ShivIntelligence(private val context: Context, val localBrain: ShivBrainHub = ShivBrainHub(context)) {
    fun local(message: String, notes: List<String>): String {
        val lower = message.trim().lowercase()
        val action = ShivPlanner.parse(message)
        if (action.kind != ActionKind.UNKNOWN) return when (action.kind) {
            ActionKind.NOTES -> "I can save this locally. Review the action to confirm."
            ActionKind.REMINDER -> "I can schedule this reminder. Review it before scheduling."
            else -> "I've prepared a " + action.kind.name.lowercase() + " action. Review before opening the target app."
        }
        return when {
            lower.contains("what do you remember") || lower == "memories" ->
                if (notes.isEmpty()) "There are no saved memories." else notes.takeLast(6).joinToString("\n• ", "Saved notes:\n• ")
            lower.startsWith("plan ") -> "Plan prepared with " + ShivPlanner.plan(message.drop(5)).steps.size + " step(s). Review each action."
            lower.contains("what can you do") || lower == "help" ->
                "I can help with local encrypted notes, multi-step plans, reminders, voice, and reviewed Android actions. Cloud reasoning is optional in Settings."
            lower.contains("privacy") -> "Local mode keeps text on device. Cloud mode sends text only when enabled explicitly."
            else -> "I'm in private local mode. I can understand supported actions and store notes, but open-ended answers need an on-device model or a cloud provider you configure in Settings."
        }
    }
    suspend fun respond(message: String, notes: List<String>, config: AssistantConfiguration, apiKey: String): String {
        val kind = ShivPlanner.parse(message).kind
        // Deterministic command handling is not represented as neural reasoning.
        if (kind != ActionKind.UNKNOWN) return local(message, notes)
        if (config.mode == IntelligenceMode.LOCAL) {
            return try { localBrain.infer(message) }
            catch (_: Exception) {
                "No on-device AI model loaded. Import your router or conversation model in Model Manager. Supported deterministic commands still work offline."
            }
        }
        if (config.mode == IntelligenceMode.HYBRID && !CloudSafety.maySend(config)) {
            return try { localBrain.infer(message) }
            catch (_: Exception) { "No local model loaded and cloud transmission is disabled." }
        }
        if (!CloudSafety.maySend(config)) return "Cloud mode requires an explicit opt-in."
        return try { completeCloud(message, config, apiKey) }
        catch (e: Exception) {
            if (config.mode == IntelligenceMode.HYBRID) {
                try { localBrain.infer(message) }
                catch (_: Exception) { "Cloud failed, and no local model is loaded: " + (e.message ?: "network issue") }
            } else "Cloud request failed: " + (e.message ?: "network issue")
        }
    }
    private suspend fun completeCloud(message: String, settings: AssistantConfiguration, apiKey: String): String =
        withContext(Dispatchers.IO) {
            require(CloudSafety.endpointAllowed(settings.endpoint)) { "Only HTTPS or explicit loopback endpoints are accepted" }
            val endpoint = URL(settings.endpoint)
            val conn = endpoint.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 15000
                conn.readTimeout = 45000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                if (apiKey.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer " + apiKey)
                val body = JSONObject().put("model", settings.model)
                    .put("messages", JSONArray().put(JSONObject()
                        .put("role", "system")
                        .put("content", "You are Shiv, an Android personal assistant. Do not claim to perform device actions: the app requires explicit action review. Be helpful and accurate."))
                        .put(JSONObject().put("role", "user").put("content", message)))
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = conn.responseCode
                if (code !in 200..299) throw IllegalStateException("Provider HTTP " + code)
                val response = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                response.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            } finally { conn.disconnect() }
        }
    suspend fun describeImage(
        uri: Uri, config: AssistantConfiguration, key: String
    ): String = withContext(Dispatchers.IO) {
        require(CloudSafety.maySend(config)) { "Enable cloud transmission to analyze selected images" }
        val type = context.contentResolver.getType(uri) ?: "image/jpeg"
        require(type in setOf("image/jpeg","image/png","image/webp")) { "Unsupported image format" }
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Image unavailable")
        require(bytes.size <= 3_000_000) { "Image must be at most 3 MB" }
        val dataUri = "data:" + type + ";base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        val json = JSONObject().put("model", config.model)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put(
                "content", JSONArray()
                    .put(JSONObject().put("type", "text").put("text", "Describe this image accurately."))
                    .put(JSONObject().put("type", "image_url")
                        .put("image_url", JSONObject().put("url", dataUri)))
            )))
        val conn = URL(config.endpoint).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"; conn.doOutput = true
            conn.connectTimeout = 15000; conn.readTimeout = 45000
            conn.setRequestProperty("Content-Type", "application/json")
            if (key.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer " + key)
            conn.outputStream.use { it.write(json.toString().toByteArray()) }
            if (conn.responseCode !in 200..299) throw IllegalStateException("Vision provider HTTP " + conn.responseCode)
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                .getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        } finally { conn.disconnect() }
    }
}
