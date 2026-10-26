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
import com.shiv.assistant.vision.ShivOfflineOcr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeoutOrNull
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
    var dailyComputeBudgetSeconds by mutableIntStateOf(600)
        private set
    var lastResourceDecision by mutableStateOf("Not yet evaluated")
        private set
    var sensingPaused by mutableStateOf(false)
        private set
    var ocrPreview by mutableStateOf("")
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
    var pendingNeuralTool by mutableStateOf<BrainTool.Proposed?>(null)
        private set
    var visionCandidate by mutableStateOf<Uri?>(null)
        private set
    var working by mutableStateOf(false)
        private set
    var notice by mutableStateOf("Local-first • Encrypted • You control every action")
        private set
    var notificationSummaries by mutableStateOf(false)
    var reminderTier by mutableStateOf(AuthorityTier.ASK_FIRST)
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
            reminderTier = runCatching {
                AuthorityTier.valueOf(vault.get("reminder_authority") ?: "ASK_FIRST")
            }.getOrDefault(AuthorityTier.ASK_FIRST).let {
                if (it == AuthorityTier.ACT_THEN_TELL) it else AuthorityTier.ASK_FIRST
            }
            sensingPaused = vault.get("sensing_paused") == "yes"
            guestMode = vault.get("guest_mode") == "yes"
            privacyZone = vault.get("privacy_zone") == "yes"
            speechLocale = vault.get("speech_locale") ?: "en-IN"
            powerMode = runCatching { PowerMode.valueOf(vault.get("power_mode") ?: "BALANCED") }
                .getOrDefault(PowerMode.BALANCED)
            dailyComputeBudgetSeconds = vault.get("daily_compute_budget_seconds")?.toIntOrNull()
                ?.takeIf { it in 60..3600 } ?: 600
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
    fun updateReminderTier(requested: AuthorityTier) {
        require(requested in setOf(AuthorityTier.ASK_FIRST, AuthorityTier.ACT_THEN_TELL)) {
            "Only reviewed and reversible reminder authority is supported"
        }
        vault.put("reminder_authority", requested.name)
        reminderTier = requested
        audit.add("REMINDER_AUTHORITY", "Owner explicitly set " + requested.name,
            "ASK_FIRST", true)
        notice = if (requested == AuthorityTier.ASK_FIRST)
            "Reminder confirmation enabled" else "Reminder auto-scheduling enabled; undo latest reminder is available"
    }
    fun maySuggestReminderAutonomy(): Boolean =
        reminderTier == AuthorityTier.ASK_FIRST &&
            AuthorityPolicy.maySuggestPromotion("REMINDER",
                audit.recent().count { it.action == "REMINDER" && it.accepted })
    fun undoLatestReminder() {
        val saved = vault.get("last_reminder_job")?.takeIf { it.isNotBlank() } ?: run {
            notice = "There is no previously scheduled reminder to cancel"
            return
        }
        val id = runCatching { java.util.UUID.fromString(saved) }.getOrNull() ?: run {
            notice = "Reminder cancellation reference invalid"
            return
        }
        WorkManager.getInstance(context).cancelWorkById(id)
        vault.put("last_reminder_job", "")
        audit.add("REMINDER_UNDO", "Owner requested cancellation of latest reminder",
            "ACT_THEN_TELL", true)
        notice = "Cancellation requested. A reminder already delivered cannot be recalled."
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
        if (value) ocrPreview = ""
        vault.put("sensing_paused", if (value) "yes" else "no")
        sensingPaused = value
        if (!value) WorkManager.getInstance(context).enqueueUniqueWork(
            "shiv_encrypted_signal_worker", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ShivSignalWorker>().build())
        audit.add("SENSING", if (value) "Owner paused all optional sensing" else "Owner resumed sensing",
            "ASK_FIRST", true)
    }
    fun updateGuestMode(value: Boolean) {
        if (value) { privateUnlocked = false; ocrPreview = "" }
        vault.put("guest_mode", if (value) "yes" else "no")
        guestMode = value
        audit.add("GUEST_MODE", if (value) "Guest mode enabled" else "Guest mode disabled", "FREE", true)
    }
    fun updatePrivacyZone(value: Boolean) {
        if (value) ocrPreview = ""
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
    fun changeDailyComputeBudgetSeconds(seconds: Int) {
        require(seconds in 60..3600) { "Daily model budget must be 1 to 60 minutes" }
        vault.put("daily_compute_budget_seconds", seconds.toString())
        dailyComputeBudgetSeconds = seconds
        notice = "Daily local-model budget updated; urgent reminders are never blocked"
    }
    fun refreshPermissions() {
        permissionReadings.clear()
        permissionReadings.addAll(ShivPermissionInspector.read(context))
    }
    fun openAppPermissions() = ShivPermissionInspector.applicationSettings(context)
    fun refreshDeviceStats() { telemetry = ShivDeviceMonitor.read(context) }
    fun leaseForConversation(): ComputeLease? {
        val stats = telemetry?.asSnapshot(false) ?: return null
        return ResourceGovernor.decide(stats, WorkClass.CONVERSATION, powerMode,
            energy.modelSecondsToday(), dailyComputeBudgetSeconds)
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
            val lease = stats?.let {
                ResourceGovernor.decide(it, WorkClass.CONVERSATION,
                    powerMode, energy.modelSecondsToday(), dailyComputeBudgetSeconds)
            } ?: error("Device resource readings unavailable; model loading stopped safely")
            lastResourceDecision = lease.reason
            require(lease.allowed) { lease.reason }
            if (item.sizeBytes > 2_000_000_000L) {
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
        if (normalized.contains("why did you remind me") ||
            normalized.contains("why was that reminder") ||
            normalized.contains("why did you do that")) {
            val kind = if (normalized.contains("remind")) "REMINDER" else null
            val last = audit.recent().firstOrNull { kind == null || it.action == kind }
            append("assistant", if (last == null)
                "I don't have a stored decision trace for that action; I won't invent a reason."
            else "Stored decision: " + last.action + "\nReason: " + last.reason +
                "\nAuthority tier: " + last.tier + "\nResult: " +
                if (last.accepted) "approved (external completion may be unverified)"
                else "rejected or failed")
            return
        }
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
        val rule = ReminderRules.parse(question)
        if (rule != null) {
            when (rule) {
                is ReminderRuleCommand.SilenceBefore -> {
                    vault.put("reminder_earliest_minute", rule.minuteOfDay.toString())
                    audit.add("REMINDER_RULE", "Owner asked to silence reminders before " +
                        ReminderRules.format(rule.minuteOfDay), "FREE", true)
                    append("assistant", "Rule saved: I will not notify you about reminders before " +
                        ReminderRules.format(rule.minuteOfDay) + " local phone time.")
                }
                ReminderRuleCommand.Clear -> {
                    vault.put("reminder_earliest_minute", "")
                    audit.add("REMINDER_RULE", "Owner removed reminder restriction", "FREE", true)
                    append("assistant", "Reminder restriction cleared.")
                }
            }
            return
        }
        if (normalized.contains("what is my reminder rule") ||
            normalized.contains("what's my reminder rule")) {
            val minute = vault.get("reminder_earliest_minute")?.toIntOrNull()
            append("assistant", if (minute == null) "No reminder time restriction is saved."
                else "Reminders before " + ReminderRules.format(minute) + " are suppressed.")
            return
        }
        val proposal = ShivPlanner.parse(question)
        if (proposal.kind == ActionKind.NOTES) {
            saveNote(proposal.argument)
            append("assistant", "Saved to encrypted Shiv memory.")
            return
        }
        if (proposal.kind == ActionKind.REMINDER &&
            reminderTier == AuthorityTier.ACT_THEN_TELL) {
            val scheduled = ShivReminders.schedule(context, proposal.argument)
            if (scheduled != null) vault.put("last_reminder_job", scheduled.toString())
            audit.add("REMINDER",
                if (scheduled == null) "Invalid reminder timing" else
                    "Owner-authorized automatic reminder: " + proposal.argument.take(160),
                "ACT_THEN_TELL", scheduled != null)
            append("assistant", if (scheduled == null)
                "I could not schedule that reminder. Use: remind me in 10 minutes to ..."
                else "Reminder scheduled under your ACT_THEN_TELL setting. Use Settings > Undo latest reminder to cancel it.")
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
                // Never treat the displayed Status snapshot as a live permission to run.
                // Re-measure immediately before inference, enforcing actual daily use.
                refreshDeviceStats()
                val lease = leaseForConversation()
                lastResourceDecision = lease?.reason ?: "Device readings unavailable"
                val loadedIsLarge = models.any { model ->
                    model.sizeBytes > 2_000_000_000L &&
                        localNeural.modelId?.endsWith("/" + model.id + ".litertlm") == true
                }
                if (lease == null || !lease.allowed || (loadedIsLarge && !lease.allowLargeModel)) {
                    append("assistant", if (lease == null)
                        "Local inference paused: device resource readings are unavailable."
                    else if (!lease.allowed) "Local inference paused: " + lease.reason
                    else "Large local model paused to protect device resources. Unload it and select a small model.")
                } else {
                    val start = System.nanoTime()
                    val answer = try {
                        // Bound wall time as well as output size; an unresponsive model
                        // must never consume an unlimited discretionary compute lease.
                        withTimeoutOrNull(lease.maxModelSeconds.toLong() * 1000L) {
                            localScheduler.infer(question, 256, InferenceUrgency.CONVERSATION)
                        }
                    } finally {
                        energy.record("local_inference",
                            (System.nanoTime() - start) / 1_000_000)
                    }
                    if (answer == null) {
                        localScheduler.interrupt()
                        append("assistant", "Local inference exceeded its " +
                            lease.maxModelSeconds + "-second resource lease and was interrupted.")
                    } else {
                        localMetrics = (localNeural as? LiteRtBrain)?.lastMetrics
                        when (answer) {
                            is BrainResult.Text -> handleLocalNeuralText(answer.value)
                            is BrainResult.Unavailable -> append("assistant", answer.reason)
                            is BrainResult.Failed -> append("assistant", answer.reason)
                        }
                    }
                }
            } else {
                val response = brain.respond(question, notes, config, apiKey)
                append("assistant", response)
            }
        } catch (e: Exception) {
            append("assistant", "I could not process that request: " + (e.message ?: "unknown error"))
        } finally { working = false }
    }
    private fun handleLocalNeuralText(reply: String) {
        val trimmed = reply.trim()
        if (!trimmed.startsWith("{")) {
            append("assistant", reply)
            return
        }
        when (val response = StructuredActions.parse(trimmed)) {
            is BrainTool.Quiet -> {
                if (response.reason == "model chose silence") {
                    notice = "Local model chose no action"
                } else {
                    append("assistant", "The local model returned an invalid action. No action was taken.")
                }
            }
            is BrainTool.Proposed -> {
                when (response.tool) {
                    "ask_user" -> append("assistant", response.args.getValue("question"))
                    "create_event", "update_event", "set_reminder", "save_memory", "save_rule" -> {
                        pendingNeuralTool = response
                        append("assistant", "Local model proposed " + response.tool +
                            ". Review the exact arguments and approve or reject.")
                    }
                    else -> append("assistant", "Unsupported model-proposed tool '" +
                        response.tool + "' was not executed.")
                }
            }
        }
    }
    fun rejectNeuralTool() {
        val tool = pendingNeuralTool ?: return
        pendingNeuralTool = null
        audit.add("AI_" + tool.tool.uppercase(), "Owner declined model proposal", "ASK_FIRST", false)
        notice = "Model-proposed action rejected"
    }
    fun approveNeuralTool() {
        val tool = pendingNeuralTool ?: return
        pendingNeuralTool = null
        val policy = AuthorityPolicy.authorize(tool.tool, AuthorityTier.ASK_FIRST,
            hasPermission = true, explicitConfirmation = true)
        if (policy.decision != ActionAuthority.EXECUTE) {
            audit.add("AI_" + tool.tool.uppercase(), policy.reason, policy.tier.name, false)
            notice = policy.reason
            return
        }
        val outcome = runCatching {
            when (tool.tool) {
                "create_event" -> {
                    agenda.add(tool.args.getValue("title"),
                        tool.args.getValue("starts_at").replace('T', ' '), AgendaKind.EVENT)
                    agendaItems.clear(); agendaItems.addAll(agenda.list())
                    "Event saved to encrypted agenda"
                }
                "update_event" -> {
                    agenda.rename(tool.args.getValue("event_id"), tool.args.getValue("title"))
                    agendaItems.clear(); agendaItems.addAll(agenda.list())
                    "Existing event title updated"
                }
                "set_reminder" -> {
                    val id = ShivReminders.scheduleAt(context, tool.args.getValue("text"),
                        tool.args.getValue("when")) ?: error("Invalid or past reminder time")
                    vault.put("last_reminder_job", id.toString())
                    "Reminder scheduled approximately by WorkManager"
                }
                "save_memory" -> {
                    knowledge.save(tool.args.getValue("fact").take(2048),
                        MemoryLayer.SEMANTIC, source = "owner approved model suggestion",
                        explicit = true)
                    "Owner-approved fact saved with provenance"
                }
                "save_rule" -> {
                    when (val rule = ReminderRules.parse(tool.args.getValue("rule"))) {
                        is ReminderRuleCommand.SilenceBefore ->
                            vault.put("reminder_earliest_minute", rule.minuteOfDay.toString())
                        ReminderRuleCommand.Clear -> vault.put("reminder_earliest_minute", "")
                        null -> error("Unsupported or ambiguous rule")
                    }
                    "Owner-approved reminder rule saved"
                }
                else -> error("Tool not authorized for automatic execution")
            }
        }
        outcome.onSuccess {
            audit.add("AI_" + tool.tool.uppercase(),
                "Owner approved deterministic execution: " + it, "ASK_FIRST", true)
            notice = it
        }.onFailure {
            audit.add("AI_" + tool.tool.uppercase(),
                "Validation or execution rejected", "ASK_FIRST", false)
            notice = "Model proposal rejected: " + (it.message ?: "invalid arguments")
        }
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
            ActionKind.REMINDER -> {
                val job = ShivReminders.schedule(context, action.argument)
                if (job != null) vault.put("last_reminder_job", job.toString())
                job != null
            }
            else -> ShivActions.launch(context, action)
        }
        audit.add(action.kind.name,
            if (accepted && action.kind == ActionKind.REMINDER)
                "Owner explicitly approved reminder: " + action.argument.take(160)
            else if (accepted) "Owner confirmed; action handed to Android (external completion unverified)"
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
    fun clearOcrPreview() { ocrPreview = "" }
    suspend fun scanOfflineImage(uri: Uri) {
        if (working || sensingPaused || guestMode || privacyZone) {
            notice = "Sensing is paused; local OCR was not started"
            return
        }
        working = true
        ocrPreview = ""
        try {
            val start = System.nanoTime()
            val original = ShivOfflineOcr.scan(context, uri)
            energy.record("offline_ocr", (System.nanoTime() - start) / 1_000_000)
            // SAF providers do not identify the original source app; never persist,
            // learn, send or infer from an image without a verifiable source.
            val safe = PrivacyPolicy.safeExtractedFact("owner.selected.ocr", original)
            if (safe == null) {
                notice = "No safe Latin text found (or sensitive/OTP content rejected)"
            } else {
                ocrPreview = safe
                notice = "Temporary on-device Latin OCR preview; NOT stored, NOT sent"
            }
        } catch (e: Exception) {
            notice = "Offline OCR failed: " + (e.message ?: "unsupported image")
        } finally {
            working = false
        }
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
