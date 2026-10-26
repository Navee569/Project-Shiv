package com.shiv.assistant

import android.content.Context
import android.net.Uri
import android.provider.Settings
import android.content.Intent
import androidx.compose.runtime.*
import com.shiv.assistant.ai.ShivIntelligence
import com.shiv.assistant.ai.LiteRtBrain
import com.shiv.assistant.ai.LocalInferenceMetrics
import com.shiv.assistant.ai.LocalInferenceScheduler
import com.shiv.assistant.ai.InferenceUrgency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.shiv.assistant.automation.ShivActions
import com.shiv.assistant.automation.ShivReminders
import com.shiv.assistant.automation.ShivSignalWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.shiv.assistant.data.*
import com.shiv.assistant.diagnostics.*
import com.shiv.assistant.domain.*
import com.shiv.assistant.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.time.LocalDate

data class ChatEntry(val role: String, val text: String)

/** Owner-controlled app state. The existing cloud AI implementation is left intact. */
class ShivController(private val context: Context) {
    val vault = ShivVault(context)
    private val brain = ShivIntelligence(context)
    private val localNeural: Brain = LiteRtBrain(context)
    private val localScheduler = LocalInferenceScheduler(localNeural,
        CoroutineScope(SupervisorJob() + Dispatchers.Default))
    val localQueueLength: Int get() = localScheduler.waitingCount
    var localMetrics by mutableStateOf<LocalInferenceMetrics?>(null)
        private set
    var neuralReady by mutableStateOf(false)
        private set
    val knowledge = ShivKnowledge(vault)
    val agenda = ShivAgenda(vault)
    val agendaItems = mutableStateListOf<AgendaItem>()
    val audit = ShivAuditTrail(vault)
    val energy = ShivEnergyLedger(vault)
    private val backup = ShivEncryptedBackup(context, vault)
    val signalQueue = ShivSignalQueue(vault)
    private val modelCatalog = ShivModelCatalog(context, vault)
    val messages = mutableStateListOf<ChatEntry>()
    val notes = mutableStateListOf<String>()
    val macros = mutableStateListOf<String>()
    var models by mutableStateOf<List<ModelCandidate>>(emptyList())
        private set
    val permissionReadings = mutableStateListOf<PermissionReading>()
    var telemetry by mutableStateOf<DeviceStats?>(null)
        private set
    var privateUnlocked by mutableStateOf(false)
        private set
    var speechLocale by mutableStateOf("en-IN")
        private set
    var powerMode by mutableStateOf(PowerMode.BALANCED)
        private set
    var sensingPaused by mutableStateOf(false)
        private set
    var guestMode by mutableStateOf(false)
        private set
    var privacyZone by mutableStateOf(false)
        private set
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
            sensingPaused = vault.get("sensing_paused") == "yes"
            guestMode = vault.get("guest_mode") == "yes"
            privacyZone = vault.get("privacy_zone") == "yes"
            speechLocale = vault.get("speech_locale") ?: "en-IN"
            powerMode = runCatching { PowerMode.valueOf(vault.get("power_mode") ?: "BALANCED") }
                .getOrDefault(PowerMode.BALANCED)
            messages.addAll(vault.history().map { ChatEntry(it.first, it.second) })
            notes.addAll(vault.notes())
            val persisted = JSONArray(vault.get("macros") ?: "[]")
            for (i in 0 until persisted.length()) macros.add(persisted.getString(i))
            models = modelCatalog.all()
            agendaItems.addAll(agenda.list())
        }.onFailure { notice = "Encrypted data could not be loaded. No information was overwritten." }
        if (messages.isEmpty()) messages.add(ChatEntry("assistant",
            "SHIV ONLINE. Local command handling is active; neural local inference is not installed. Cloud and hybrid remain optional in Settings."))
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
    fun updateSensingPaused(value: Boolean) {
        vault.put("sensing_paused", if (value) "yes" else "no")
        sensingPaused = value
        if (!value) WorkManager.getInstance(context).enqueueUniqueWork(
            "shiv_encrypted_signal_worker", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ShivSignalWorker>().build())
        audit.add("SENSING", if (value) "Owner paused all optional sensing" else "Owner resumed sensing",
            "ASK_FIRST", true)
    }
    fun updateGuestMode(value: Boolean) {
        if (value) privateUnlocked = false
        vault.put("guest_mode", if (value) "yes" else "no")
        guestMode = value
        audit.add("GUEST_MODE", if (value) "Guest mode enabled" else "Guest mode disabled", "FREE", true)
    }
    fun updatePrivacyZone(value: Boolean) {
        vault.put("privacy_zone", if (value) "yes" else "no")
        privacyZone = value
        audit.add("PRIVACY_ZONE", if (value) "Privacy zone manually enabled" else "Privacy zone disabled",
            "FREE", true)
    }
    fun denyPackage(packageName: String) {
        val name = packageName.trim().lowercase()
        require(name.matches(Regex("[a-z0-9_]+(?:[.][a-z0-9_]+)+"))) { "Enter a valid Android package identifier" }
        val old = vault.get("denied_packages").orEmpty().lineSequence().filter { it.isNotBlank() }.toSet()
        vault.put("denied_packages", (old + name).sorted().joinToString("\n"))
        notice = "App added to the denylist; content from it is not stored"
    }
    fun extraDeniedPackages() = vault.get("denied_packages").orEmpty()
    fun onBiometricAuthenticated() {
        if (!guestMode) privateUnlocked = true
    }
    fun lockPrivate() { privateUnlocked = false }
    fun changeSpeechLocale(tag: String) {
        require(tag in setOf("en-IN", "kn-IN", "te-IN")) { "Unsupported speech locale" }
        speechLocale = tag
        vault.put("speech_locale", tag)
    }
    fun speechMessage(value: String) { notice = value.take(300) }
    fun changePowerMode(mode: PowerMode) {
        powerMode = mode
        vault.put("power_mode", mode.name)
    }
    fun refreshPermissions() {
        permissionReadings.clear()
        permissionReadings.addAll(ShivPermissionInspector.read(context))
    }
    fun openAppPermissions() = ShivPermissionInspector.applicationSettings(context)
    fun refreshDeviceStats() { telemetry = ShivDeviceMonitor.read(context) }
    fun leaseForConversation(): ComputeLease? {
        val stats = telemetry?.asSnapshot(false) ?: return null
        return ResourceGovernor.decide(stats, WorkClass.CONVERSATION, powerMode, 0, 600)
    }
    suspend fun registerModel(uri: Uri) {
        working = true
        try {
            val item = modelCatalog.register(uri)
            models = modelCatalog.all()
            notice = "Model '" + item.name + "' registered, but runtime compatibility is NOT verified"
        } catch (e: Exception) {
            notice = "Model selection failed: " + (e.message ?: "unknown error")
        } finally { working = false }
    }
    suspend fun loadModel(id: String) {
        if (working) return
        val item = models.firstOrNull { it.id == id } ?: return
        working = true
        try {
            refreshDeviceStats()
            val stats = telemetry?.asSnapshot(false)
            if (item.sizeBytes > 2_000_000_000L && stats != null) {
                val lease = ResourceGovernor.decide(stats, WorkClass.CONVERSATION,
                    powerMode, 0, 600)
                require(lease.allowLargeModel) { "Device resources insufficient for large model. Try while charging." }
            }
            notice = "Preparing model (large files require a private copy) ..."
            val path = modelCatalog.stageForLoad(id)
            val start = System.nanoTime()
            val outcome = localNeural.load(path)
            energy.record("local_model_load", (System.nanoTime() - start) / 1_000_000)
            neuralReady = outcome is BrainResult.Text
            notice = when (outcome) {
                is BrainResult.Text -> "Offline neural model loaded. Local chat can now use it."
                is BrainResult.Unavailable -> outcome.reason
                is BrainResult.Failed -> outcome.reason
            }
            models = modelCatalog.all()
        } catch (e: Exception) {
            neuralReady = false
            notice = "Local model could not be loaded: " + (e.message ?: "unknown error")
        } finally { working = false }
    }
    suspend fun interruptInference() {
        localScheduler.interrupt()
    }
    suspend fun unloadModel() {
        localNeural.unload()
        neuralReady = false
        notice = "Local model unloaded"
    }
    fun removeModel(id: String) {
        modelCatalog.remove(id)
        models = modelCatalog.all()
        notice = "Model reference removed (original file remains untouched)"
    }
    private fun append(role: String, text: String) {
        messages.add(ChatEntry(role, text))
        vault.addChat(role, text)
    }
    suspend fun submit(message: String) {
        val question = message.trim()
        if (question.isEmpty() || working) return
        append("user", question)
        val normalized = question.lowercase()
        if (normalized.contains("what's on tomorrow") ||
            normalized.contains("what is on tomorrow") ||
            normalized.contains("schedule tomorrow")) {
            val items = agenda.onDay(LocalDate.now().plusDays(1))
            append("assistant", if (items.isEmpty()) "No saved events or tasks for tomorrow."
            else items.joinToString("\n") {
                AgendaDates.formatLocal(it.dueEpochMs).substringAfter(' ') + " — " +
                    it.kind.name + ": " + it.title + (if (it.completed) " (done)" else "")
            })
            return
        }
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
            if (config.mode == IntelligenceMode.LOCAL && localNeural.modelId != null) {
                val start = System.nanoTime()
                val answer = localScheduler.infer(question, 256, InferenceUrgency.CONVERSATION)
                localMetrics = (localNeural as? LiteRtBrain)?.lastMetrics
                energy.record("local_inference", (System.nanoTime() - start) / 1_000_000)
                append("assistant", when (answer) {
                    is BrainResult.Text -> answer.value
                    is BrainResult.Unavailable -> answer.reason
                    is BrainResult.Failed -> answer.reason
                })
            } else {
                val response = brain.respond(question, notes, config, apiKey)
                append("assistant", response)
            }
        } catch (e: Exception) {
            append("assistant", "I could not process that request: " + (e.message ?: "unknown error"))
        } finally { working = false }
    }
    fun propose(action: ActionProposal) { pending = action }
    fun reject() {
        pending?.let { audit.add(it.kind.name, "Owner declined proposed action", "ASK_FIRST", false) }
        pending = null
        notice = "Action cancelled"
    }
    private fun actionAuthority(kind: ActionKind): String = when(kind) {
        ActionKind.UPI -> "PAYMENT"
        ActionKind.SMS, ActionKind.EMAIL, ActionKind.TELEGRAM, ActionKind.WHATSAPP -> "SEND_MESSAGE"
        ActionKind.SHARE -> "SOCIAL_PUBLISH"
        else -> kind.name
    }
    fun approve() {
        val action = pending ?: return
        pending = null
        val decision = AuthorityPolicy.authorize(actionAuthority(action.kind),
            AuthorityTier.ASK_FIRST, true, true)
        if (decision.decision != ActionAuthority.EXECUTE) {
            audit.add(action.kind.name, decision.reason, decision.tier.name, false)
            notice = decision.reason
            return
        }
        val accepted = when (action.kind) {
            ActionKind.NOTES -> { saveNote(action.argument); true }
            ActionKind.REMINDER -> ShivReminders.schedule(context, action.argument) != null
            else -> ShivActions.launch(context, action)
        }
        audit.add(action.kind.name,
            if (accepted) "Owner confirmed; action handed to Android (external completion unverified)"
            else "Owner confirmed; action failed or unsupported",
            decision.tier.name, accepted)
        notice = when {
            !accepted -> "Action could not be scheduled or opened"
            action.kind == ActionKind.REMINDER -> "Reminder scheduled locally"
            else -> "External app opened for owner review; completion not verified"
        }
    }
    fun saveNote(value: String) {
        if (value.trim().isEmpty()) return
        vault.addNote(value.trim())
        knowledge.save(value.trim().take(2048))
        notes.clear(); notes.addAll(vault.notes())
        notice = "Encrypted note saved"
    }
    fun deleteStructuredMemory(id: String) {
        knowledge.remove(id)
        notice = "Selected structured memory deleted"
    }
    fun correctStructuredMemory(id: String, correction: String) {
        if (correction.isBlank()) return
        knowledge.correct(id, correction)
        notice = "Explicit correction saved; older belief superseded"
    }
    suspend fun exportEncryptedBackup(destination: Uri, passphrase: String) {
        if (!privateUnlocked || guestMode) {
            notice = "Unlock private memory before exporting"
            return
        }
        working = true
        try {
            val count = backup.export(destination, passphrase)
            audit.add("ENCRYPTED_EXPORT", "Owner exported " + count + " records", "ASK_FIRST", true)
            notice = "Encrypted backup written: " + count + " records (model files are not included)"
        } catch (e: Exception) {
            notice = "Encrypted export failed: " + (e.message ?: "unknown error")
        } finally { working = false }
    }
    suspend fun restoreEncryptedBackup(source: Uri, passphrase: String) {
        if (!privateUnlocked || guestMode) {
            notice = "Unlock private memory before restoring"
            return
        }
        working = true
        try {
            val count = backup.restore(source, passphrase)
            audit.add("ENCRYPTED_RESTORE", "Owner imported " + count + " encrypted records",
                "ASK_FIRST", true)
            notice = "Restored " + count + " records; restart Shiv to refresh all screens"
        } catch (e: Exception) {
            notice = "Encrypted restore rejected: " + (e.message ?: "unknown error")
        } finally { working = false }
    }
    fun createAgendaItem(title: String, dateTime: String, kind: AgendaKind) {
        runCatching {
            val item = agenda.add(title, dateTime, kind)
            refreshAgenda()
            audit.add(kind.name, "Owner saved dated agenda item " + item.id, "ASK_FIRST", true)
            notice = kind.name.lowercase().replaceFirstChar { it.uppercase() } + " saved for " + dateTime
        }.onFailure { notice = "Could not save event/task: " + (it.message ?: "invalid date") }
    }
    fun completeAgendaItem(id: String, done: Boolean) {
        agenda.markCompleted(id, done)
        refreshAgenda()
        notice = if (done) "Task marked completed" else "Task marked outstanding"
    }
    fun removeAgendaItem(id: String) {
        agenda.remove(id)
        refreshAgenda()
        audit.add("AGENDA_DELETE", "Owner deleted agenda item " + id, "ASK_FIRST", true)
        notice = "Agenda item deleted"
    }
    private fun refreshAgenda() {
        agendaItems.clear()
        agendaItems.addAll(agenda.list())
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
        audit.add("CHAT_DELETE", "Owner cleared conversation history", "ASK_FIRST", true)
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
