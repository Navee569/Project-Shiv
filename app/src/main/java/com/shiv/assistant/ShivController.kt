package com.shiv.assistant

import android.content.Context
import android.net.Uri
import android.provider.Settings
import android.content.Intent
import androidx.compose.runtime.*
import com.shiv.assistant.ai.ShivIntelligence
import com.shiv.assistant.automation.ShivActions
import com.shiv.assistant.automation.ShivReminders
import com.shiv.assistant.data.ShivVault
import com.shiv.assistant.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class ChatEntry(val role: String, val text: String)

/**
 * State and behavior belong to Shiv, not a renamed third-party App class.
 * External operations are always placed in a review queue first.
 */
class ShivController(private val context: Context) {
    val vault = ShivVault(context)
    private val brain = ShivIntelligence(context)
    val messages = mutableStateListOf<ChatEntry>()
    val notes = mutableStateListOf<String>()
    val macros = mutableStateListOf<String>()
    var config by mutableStateOf(AssistantConfiguration())
        private set
    var apiKey by mutableStateOf("")
        private set
    var pending by mutableStateOf<ActionProposal?>(null)
        private set
    var visionCandidate by mutableStateOf<Uri?>(null)
        private set
    var working by mutableStateOf(false)
        private set
    var notice by mutableStateOf("Local-first • Encrypted • You control every action")
        private set
    var notificationSummaries by mutableStateOf(false)
        private set
    init {
        runCatching {
            config = AssistantConfiguration(
                mode = runCatching { IntelligenceMode.valueOf(vault.get("brain_mode") ?: "LOCAL") }.getOrDefault(IntelligenceMode.LOCAL),
                endpoint = vault.get("endpoint") ?: "https://api.openai.com/v1/chat/completions",
                model = vault.get("model") ?: "gpt-4.1-mini",
                allowCloudTransmission = vault.get("cloud_consent") == "yes"
            )
            apiKey = vault.get("api_secret").orEmpty()
            notificationSummaries = vault.get("notification_opt_in") == "yes"
            messages.addAll(vault.history().map { ChatEntry(it.first, it.second) })
            notes.addAll(vault.notes())
            val persisted = JSONArray(vault.get("macros") ?: "[]")
            for (i in 0 until persisted.length()) macros.add(persisted.getString(i))
        }.onFailure { notice = "Encrypted data could not be loaded. No information was overwritten." }
        if (messages.isEmpty()) messages.add(ChatEntry("assistant", "SHIV ONLINE. Local intelligence is active. Choose cloud or hybrid mode in Settings if you want an API-powered assistant."))
    }
    fun settings(mode: IntelligenceMode, endpoint: String, model: String, secret: String, consent: Boolean) {
        require(CloudSafety.endpointAllowed(endpoint)) { "Use an HTTPS endpoint or explicit local loopback address" }
        require(model.isNotBlank()) { "Model name cannot be empty" }
        config = AssistantConfiguration(mode, endpoint.trim(), model.trim(), consent)
        vault.put("brain_mode", mode.name)
        vault.put("endpoint", endpoint.trim())
        vault.put("model", model.trim())
        vault.put("cloud_consent", if (consent) "yes" else "no")
        vault.put("api_secret", secret)
        apiKey = secret
        notice = "Intelligence configuration saved securely"
    }
    private fun append(role: String, text: String) {
        messages.add(ChatEntry(role, text))
        vault.addChat(role, text)
    }
    suspend fun submit(message: String) {
        val question = message.trim()
        if (question.isEmpty() || working) return
        append("user", question)
        val proposal = ShivPlanner.parse(question)
        if (proposal.kind == ActionKind.NOTES) {
            saveNote(proposal.argument)
            append("assistant", "Saved to encrypted Shiv memory.")
            return
        }
        if (proposal.kind != ActionKind.UNKNOWN) {
            pending = proposal
            append("assistant", proposal.explanation + ". Approve or cancel the proposed action below.")
            return
        }
        working = true
        try {
            val response = brain.respond(question, notes, config, apiKey)
            append("assistant", response)
        } catch (e: Exception) {
            append("assistant", "I could not process that request: " + (e.message ?: "unknown error"))
        } finally { working = false }
    }
    fun propose(action: ActionProposal) { pending = action }
    fun reject() { pending = null; notice = "Action cancelled" }
    fun approve() {
        val action = pending ?: return
        pending = null
        when (action.kind) {
            ActionKind.NOTES -> saveNote(action.argument)
            ActionKind.REMINDER -> {
                val id = ShivReminders.schedule(context, action.argument)
                notice = if (id == null) "Use: remind me in 10 minutes to ..." else "Reminder scheduled locally"
            }
            else -> notice = if (ShivActions.launch(context, action)) "Opened external app for review"
                else "No compatible app installed, or action unavailable"
        }
    }
    fun saveNote(value: String) {
        if (value.trim().isEmpty()) return
        vault.addNote(value.trim())
        notes.clear(); notes.addAll(vault.notes())
        notice = "Encrypted note saved"
    }
    fun saveMacro(goal: String) {
        if (goal.isBlank()) return
        macros.add(goal.trim().take(512))
        val json = JSONArray(); macros.takeLast(50).forEach { json.put(it) }
        vault.put("macros", json.toString())
        notice = "Routine saved — execution always requires review"
    }
    fun clearHistory() {
        vault.clearHistory()
        messages.clear()
        notice = "Chat history erased"
    }
    fun notifications(on: Boolean) {
        notificationSummaries = on
        vault.put("notification_opt_in", if (on) "yes" else "no")
        if (!on) vault.clearNotifications()
    }
    fun chooseImage(uri: Uri?) { visionCandidate = uri }
    fun cancelImage() { visionCandidate = null }
    suspend fun approveVision() {
        val uri = visionCandidate ?: return
        visionCandidate = null
        working = true
        try {
            val description = brain.describeImage(uri, config, apiKey)
            append("assistant", "Image analysis: " + description)
        } catch (e: Exception) {
            notice = "Vision request unavailable: " + (e.message ?: "unknown error")
        } finally { working = false }
    }
    suspend fun importText(uri: Uri) {
        try {
            val text = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                    val value = it.readText()
                    require(value.length <= 100_000) { "Document exceeds 100 KB text limit" }
                    value
                } ?: error("Cannot read file")
            }
            saveNote(text.take(8192))
        } catch (e: Exception) { notice = "Import failed: " + (e.message ?: "unknown error") }
    }
    fun openNotificationSettings() {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
