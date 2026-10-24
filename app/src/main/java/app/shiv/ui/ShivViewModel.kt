package app.shiv.ui

import app.shiv.*
import app.shiv.platform.*
import app.shiv.domain.brain.BrainPort
import app.shiv.domain.brain.BrainReply
import app.shiv.domain.brain.SmsDraft
import app.shiv.domain.integration.ActionPermissionPort
import app.shiv.domain.integration.CalendarSyncPort
import app.shiv.domain.integration.DiagnosticCode
import app.shiv.domain.integration.DiagnosticArea
import app.shiv.domain.integration.DiagnosticEvent
import app.shiv.domain.integration.DiagnosticsPort
import app.shiv.domain.integration.ReminderSchedulingPort
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import app.shiv.data.repository.RoomShivViewRepository
import app.shiv.domain.repository.ShivViewRepository
import app.shiv.domain.actions.ActionAuthorizationRequest
import app.shiv.domain.actions.ActionAuthorizationOutcome
import app.shiv.domain.actions.ActionAuthorizationDecision
import app.shiv.domain.actions.ActionPermissionState
import app.shiv.domain.actions.ActionTier
import app.shiv.domain.actions.ActionAuditRepository
import app.shiv.domain.actions.ActionDispatchResult
import app.shiv.domain.actions.ActionExecutionStatus
import app.shiv.domain.actions.AuthorizedActionExecutor

sealed class PendingShivAction(val title: String, val explanation: String) {
    class SaveProfile(val name: String, val notes: String) : PendingShivAction("Update your local profile?", "Confirm to replace the profile details stored on this phone.")
    class SaveEvent(val event: EventRecord) : PendingShivAction("Save this event?", "Confirm to save ${event.title} and schedule its reminder on this phone.")
    class SaveMemory(val memory: MemoryFact) : PendingShivAction("Save this memory?", "Confirm to save: ${memory.text}")
    class SaveRule(val minutes: Int) : PendingShivAction("Change reminder rule?", "Confirm to remind you $minutes minutes before events.")
    class Reschedule(val event: EventRecord, val newStart: Long) : PendingShivAction("Reschedule this event?", "Confirm to move ${event.title} and reset its reminder.")
    class SaveDraft(val draft: ReplyDraft) : PendingShivAction("Save this reply draft?", "Confirm to save a draft for ${draft.person}.")
    class DeleteEvent(val event: EventRecord) : PendingShivAction("Delete event?", "This will remove ${event.title} from Shiv's encrypted memory.")
    class DeleteMemory(val memory: MemoryFact) : PendingShivAction("Forget this memory?", "Shiv will remove the saved fact: ${memory.text}")
    object DeleteEverything : PendingShivAction("Erase all Shiv data?", "This permanently deletes your local chats, profile, memories, events, drafts, rules, and action history.")
    class SendSms(val draft: SmsDraft) : PendingShivAction("Open a message draft?", "Shiv will open your messaging app with the text prepared for ${draft.person}. It will not send until you tap Send in that app.")
}

class ShivViewModel(app: Application) : AndroidViewModel(app) {
    private val dao: ShivViewRepository = RoomShivViewRepository(app)
    private val diagnostics: DiagnosticsPort = AndroidDiagnosticsPort()
    private val permissions: ActionPermissionPort = AndroidActionPermissionPort(app)
    private val reminders: ReminderSchedulingPort = AndroidReminderSchedulingPort(app)
    private val calendarSync: CalendarSyncPort = AndroidCalendarSyncPort(app, dao, permissions, diagnostics)
    private val actionExecutor = AuthorizedActionExecutor(ActionAuditRepository(dao::logAction), diagnostics = diagnostics)
    private val brainRuntime = BrainProvider.get(app.applicationContext)
    private val brain: BrainPort = brainRuntime
    val messages = dao.messages().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val events = dao.events(System.currentTimeMillis()).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pendingEvents = dao.pendingEvents(System.currentTimeMillis()).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val memories = dao.memories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val profile = dao.profile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val actionLog = dao.actionLog().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val drafts = dao.drafts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    // Keep permission gates current even while the user is on another tab.
    val actionTiers = dao.actionTiers().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings = dao.settings().stateIn(viewModelScope, SharingStarted.Eagerly, ShivSettings())
    val reminderLeadMinutes = MutableStateFlow(Brain.DEFAULT_LEAD_MINUTES)
    val pendingAction = MutableStateFlow<PendingShivAction?>(null)
    val lastCalendarSync = MutableStateFlow<String?>(null)
    val modelStatus = MutableStateFlow("No local chat model installed")
    val modelFiles = MutableStateFlow(false to false)
    val brainRuntimeStatus = brain.runtimeStatus
    val backupStatus = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            reminderLeadMinutes.value = dao.reminderRule(Brain.REMINDER_LEAD_KEY)?.value?.toIntOrNull() ?: Brain.DEFAULT_LEAD_MINUTES
            if (dao.profileOnce() == null) dao.saveProfile(UserProfile())
            if (dao.settingsOnce() == null) dao.saveSettings(ShivSettings())
            ProactiveWorker.schedule(getApplication())
            refreshModelStatus()
        }
    }

    fun saveTier(actionKey: String, tier: ActionTier) {
        viewModelScope.launch {
            dao.saveActionTier(ActionTierSetting(actionKey, tier.name))
            dao.logAction(ActionLogEntry(action = "Changed action safety tier: $actionKey", tier = tier.name, outcome = "Saved in encrypted local database"))
        }
    }

    fun saveSettings(value: ShivSettings) {
        val safe = value.copy(
            quietStartMinute = value.quietStartMinute.coerceIn(0, 1439),
            quietEndMinute = value.quietEndMinute.coerceIn(0, 1439),
            maxDailyInterruptions = value.maxDailyInterruptions.coerceIn(0, 20)
        )
        viewModelScope.launch {
            dao.saveSettings(safe)
            dao.logAction(ActionLogEntry(action = "Updated proactive assistant settings", tier = ActionTier.FREE.name, outcome = "Quiet ${safe.quietStartMinute}-${safe.quietEndMinute}; max ${safe.maxDailyInterruptions}/day"))
            ProactiveWorker.checkSoon(getApplication())
        }
    }

    fun importModel(uri: android.net.Uri, role: String) {
        viewModelScope.launch(Dispatchers.IO) {
            modelStatus.value = "Copying selected model into private phone storage…"
            runCatching { brainRuntime.importModel(uri, role) }
                .onSuccess {
                    refreshModelStatus()
                    modelStatus.value = "${role.replaceFirstChar(Char::uppercase)} model installed on this phone. It has not been uploaded."
                    dao.logAction(ActionLogEntry(action = "Imported a local language model", tier = ActionTier.FREE.name, outcome = role))
                }
                .onFailure { modelStatus.value = "Model import failed: ${it.message ?: it.javaClass.simpleName}" }
        }
    }

    private suspend fun refreshModelStatus() {
        modelFiles.value = brainRuntime.installedModels()
        modelStatus.value = if (modelFiles.value.first || modelFiles.value.second) "Local model file(s) installed; not loaded yet" else brain.modelStatus
    }

    fun exportBackup(uri: android.net.Uri, password: CharArray) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { EncryptedBackup(getApplication()).export(uri, password) }
                .onSuccess { backupStatus.value = "Encrypted backup saved. Keep the passphrase safe; Shiv cannot recover it." }
                .onFailure { backupStatus.value = "Backup failed: ${it.message ?: it.javaClass.simpleName}" }
                .also { password.fill('\u0000') }
        }
    }

    fun restoreBackup(uri: android.net.Uri, password: CharArray) {
        viewModelScope.launch(Dispatchers.IO) {
            val oldEvents = dao.upcomingEvents(0)
            runCatching { EncryptedBackup(getApplication()).restore(uri, password) }
                .onSuccess {
                    oldEvents.forEach { reminders.cancel(it) }
                    val lead = dao.reminderRule(Brain.REMINDER_LEAD_KEY)?.value?.toIntOrNull() ?: Brain.DEFAULT_LEAD_MINUTES
                    dao.upcomingEvents(System.currentTimeMillis()).forEach { reminders.schedule(it, lead) }
                    backupStatus.value = "Restore finished. The backup replaced the data previously in Shiv."
                }
                .onFailure { backupStatus.value = "Restore failed; existing Shiv data was left unchanged: ${it.message ?: it.javaClass.simpleName}" }
                .also { password.fill('\u0000') }
        }
    }

    fun send(text: String) {
        val input = text.trim()
        if (input.isBlank()) return
        viewModelScope.launch {
            dao.addMessage(ChatMessage(text = input, fromUser = true))
            val contextSnapshot = buildString {
                appendLine("Current time: ${java.time.ZonedDateTime.now()}")
                appendLine("Next 48 hours of events:")
                events.value.filter { it.startsAt in System.currentTimeMillis()..(System.currentTimeMillis() + 48 * 60 * 60 * 1000L) }
                    .take(12).forEach { appendLine("- id=${it.id}; ${it.title} at ${java.time.Instant.ofEpochMilli(it.startsAt)}; ${it.person.orEmpty()} ${it.place.orEmpty()}") }
                appendLine("Relevant personal memories:")
                memories.value.take(8).forEach { appendLine("- ${it.text}") }
                appendLine("Saved rules:")
                appendLine("- Remind ${reminderLeadMinutes.value} minutes before events")
                appendLine("Recent conversation:")
                messages.value.takeLast(6).forEach { appendLine("- ${if (it.fromUser) "User" else "Shiv"}: ${it.text.take(300)}") }
                appendLine("Actions already taken today:")
                actionLog.value.filter { it.createdAt >= System.currentTimeMillis() - 24 * 60 * 60 * 1000L }.take(8).forEach { appendLine("- ${it.action}: ${it.outcome}") }
            }
            val parsed = brain.respond(input, profile.value, memories.value, reminderLeadMinutes.value, contextSnapshot)
            modelStatus.value = brain.modelStatus
            var answer = parsed.text
            parsed.memory?.let {
                if (memories.value.none { old -> old.text.equals(it.text, ignoreCase = true) }) {
                    val report = actionExecutor.execute(
                        actionRequest("SAVE_MEMORY", mapOf("id" to it.id, "text" to it.text), ActionTier.FREE)
                    ) {
                        dao.saveMemory(it)
                        ActionDispatchResult("Memory saved locally", "DELETE_MEMORY:${it.id}")
                    }
                    when (report.status) {
                        ActionExecutionStatus.AWAITING_CONFIRMATION -> {
                            pendingAction.value = PendingShivAction.SaveMemory(it)
                            answer = "I found a personal fact. I’ll save it only after you confirm."
                        }
                        ActionExecutionStatus.SUCCEEDED -> Unit
                        else -> answer = executionFailure(report, "memory")
                    }
                }
            }
            parsed.reminderLeadMinutes?.let {
                val previous = reminderLeadMinutes.value
                val report = actionExecutor.execute(
                    actionRequest("SET_REMINDER", mapOf("minutes" to it.toString()), ActionTier.FREE)
                ) {
                    updateReminderLead(it)
                    ActionDispatchResult("Reminder lead updated", "SET_REMINDER:$previous")
                }
                when (report.status) {
                    ActionExecutionStatus.AWAITING_CONFIRMATION -> {
                        pendingAction.value = PendingShivAction.SaveRule(it)
                        answer = "I can change that reminder rule. Confirm and I’ll save it."
                    }
                    ActionExecutionStatus.SUCCEEDED -> Unit
                    else -> answer = executionFailure(report, "reminder rule")
                }
            }
            parsed.event?.let { event ->
                val report = actionExecutor.execute(
                    actionRequest(
                        "CREATE_EVENT",
                        mapOf("id" to event.id, "title" to event.title, "startsAt" to event.startsAt.toString()),
                        ActionTier.FREE
                    )
                ) {
                    saveEvent(event)
                    ActionDispatchResult("Event saved and reminder scheduled", "DELETE_EVENT:${event.id}")
                }
                when (report.status) {
                    ActionExecutionStatus.AWAITING_CONFIRMATION -> {
                        pendingAction.value = PendingShivAction.SaveEvent(event)
                        answer = "I found an event to save. Confirm and I’ll add it and set the reminder."
                    }
                    ActionExecutionStatus.SUCCEEDED -> Unit
                    ActionExecutionStatus.AUDIT_UNAVAILABLE -> answer = when {
                        report.dispatched -> "The event was saved, but I couldn’t update its action record."
                        report.dispatchAttempted -> "The event action did not finish cleanly. Check the event and reminder before retrying."
                        else -> "I couldn’t securely record that action, so I didn’t save the event."
                    }
                    ActionExecutionStatus.FAILED -> answer = "The event action did not finish cleanly. Check the event and reminder before retrying."
                    else -> answer = authorizationFailure(report.authorization)
                }
            }
            parsed.reschedule?.let { request ->
                val match = events.value.filter { it.startsAt > System.currentTimeMillis() }
                    .firstOrNull { it.id == request.search || it.title.contains(request.search, true) || it.person?.contains(request.search, true) == true || request.search.contains(it.title, true) }
                if (match == null) answer = "I couldn’t find an upcoming event matching ‘${request.search}’. Nothing was changed."
                else {
                    val report = actionExecutor.execute(
                        actionRequest(
                            "RESCHEDULE",
                            mapOf("eventId" to match.id, "startsAt" to match.startsAt.toString(), "newStartsAt" to request.newStart.toString()),
                            ActionTier.ACT_THEN_TELL
                        )
                    ) {
                        val changed = match.copy(startsAt = request.newStart)
                        dao.saveEvent(changed)
                        reminders.cancel(match)
                        reminders.schedule(changed, reminderLeadMinutes.value)
                        ActionDispatchResult("Event and reminder updated", "RESCHEDULE:${match.id}:${match.startsAt}")
                    }
                    when (report.status) {
                        ActionExecutionStatus.AWAITING_CONFIRMATION -> {
                            pendingAction.value = PendingShivAction.Reschedule(match, request.newStart)
                            answer = "I can move ${match.title}. Confirm and I’ll update the event and reminder."
                        }
                        ActionExecutionStatus.SUCCEEDED -> {
                            answer = "Moved ${match.title} to ${android.text.format.DateFormat.format("EEE, MMM d 'at' h:mm a", request.newStart)}. Its reminder is reset."
                        }
                        else -> answer = executionFailure(report, "reschedule")
                    }
                }
            }
            parsed.draft?.let {
                val report = actionExecutor.execute(
                    actionRequest("DRAFT_REPLY", mapOf("person" to it.person, "body" to it.body), ActionTier.ACT_THEN_TELL)
                ) {
                    dao.saveDraft(it)
                    ActionDispatchResult("Reply draft saved")
                }
                when (report.status) {
                    ActionExecutionStatus.AWAITING_CONFIRMATION -> {
                        pendingAction.value = PendingShivAction.SaveDraft(it)
                        answer = "I prepared the reply. Confirm and I’ll save it to your drafts."
                    }
                    ActionExecutionStatus.SUCCEEDED -> Unit
                    else -> answer = executionFailure(report, "reply draft")
                }
            }
            parsed.sms?.let {
                val report = actionExecutor.execute(
                    actionRequest("SEND_MESSAGE", mapOf("person" to it.person, "body" to it.body), ActionTier.ASK_FIRST)
                ) { ActionDispatchResult("Waiting for message draft confirmation") }
                when (report.status) {
                    ActionExecutionStatus.AWAITING_CONFIRMATION -> {
                        pendingAction.value = PendingShivAction.SendSms(it)
                        answer = "I prepared a message draft. Confirm to open it; you will still tap Send in your messaging app."
                    }
                    ActionExecutionStatus.SUCCEEDED -> Unit
                    else -> answer = executionFailure(report, "message draft")
                }
            }
            parsed.deleteEverything.let { if (it) {
                val report = actionExecutor.execute(actionRequest("DELETE_DATA", emptyMap(), ActionTier.ASK_FIRST)) {
                    ActionDispatchResult("Waiting for data deletion confirmation")
                }
                when (report.status) {
                    ActionExecutionStatus.AWAITING_CONFIRMATION -> {
                        pendingAction.value = PendingShivAction.DeleteEverything
                        answer = "I can erase Shiv’s local data. Confirm to continue."
                    }
                    ActionExecutionStatus.SUCCEEDED -> Unit
                    else -> answer = executionFailure(report, "data deletion")
                }
            } }
            parsed.forgetPhrase?.let { phrase ->
                val target = memories.value.firstOrNull { it.text.contains(phrase, true) || phrase.contains(it.text, true) }
                if (target != null) {
                    val report = actionExecutor.execute(actionRequest("DELETE_DATA", mapOf("memoryId" to target.id), ActionTier.ASK_FIRST)) {
                        ActionDispatchResult("Waiting for memory deletion confirmation")
                    }
                    when (report.status) {
                        ActionExecutionStatus.AWAITING_CONFIRMATION -> {
                            pendingAction.value = PendingShivAction.DeleteMemory(target)
                            answer = "I found that saved memory. Confirm to remove it."
                        }
                        ActionExecutionStatus.SUCCEEDED -> Unit
                        else -> answer = executionFailure(report, "memory deletion")
                    }
                } else answer = "I couldn’t find a saved memory matching that, so nothing was removed."
            }
            if (answer.isNotBlank()) dao.addMessage(ChatMessage(text = answer, fromUser = false, tier = tierFor(parsed).name))
        }
    }

    private fun actionRequest(
        actionType: String,
        arguments: Map<String, String>,
        minimumTier: ActionTier,
        permissionState: ActionPermissionState? = null,
        confirmed: Boolean = false
    ) = ActionAuthorizationRequest(
            actionType = actionType,
            arguments = arguments,
            configuredTier = actionTiers.value.firstOrNull { it.actionKey == actionType }?.tier,
            minimumTier = minimumTier,
            permissionState = permissionState ?: permissions.stateFor(actionType),
            confirmed = confirmed
        )

    private fun authorizationFailure(decision: ActionAuthorizationDecision): String = when (decision.outcome) {
        ActionAuthorizationOutcome.DENY_PERMISSION -> "I don’t have permission to perform that action. Nothing was changed."
        ActionAuthorizationOutcome.DENY_INVALID_REQUEST -> "I couldn’t validate that action, so nothing was changed."
        else -> "I couldn’t authorize that action, so nothing was changed."
    }

    private fun executionFailure(report: app.shiv.domain.actions.ActionExecutionReport, subject: String): String = when (report.status) {
        ActionExecutionStatus.AUDIT_UNAVAILABLE -> when {
            report.dispatched -> "The $subject action completed, but I couldn’t update its action record."
            report.dispatchAttempted -> "The $subject action did not finish cleanly. Check its result before retrying."
            else -> "I couldn’t securely record that action, so I didn’t make the change."
        }
        ActionExecutionStatus.FAILED -> "The $subject action did not finish cleanly. Check its result before retrying."
        else -> authorizationFailure(report.authorization)
    }

    private suspend fun saveEvent(event: EventRecord) {
        dao.saveEvent(event)
        reminders.schedule(event, reminderLeadMinutes.value)
    }

    private fun tierFor(reply: BrainReply): ActionTier = when {
        reply.sms != null || reply.deleteEverything || reply.forgetPhrase != null -> ActionTier.ASK_FIRST
        reply.reschedule != null || reply.draft != null -> ActionTier.ACT_THEN_TELL
        else -> ActionTier.FREE
    }

    private suspend fun updateReminderLead(minutes: Int) {
        val safe = minutes.coerceIn(0, 1440)
        reminderLeadMinutes.value = safe
        dao.saveRule(ReminderRule(Brain.REMINDER_LEAD_KEY, safe.toString()))
        dao.upcomingEvents(System.currentTimeMillis()).forEach { reminders.schedule(it, safe) }
    }

    fun saveProfile(name: String, notes: String) {
        viewModelScope.launch {
            val report = actionExecutor.execute(actionRequest("SAVE_PROFILE", mapOf("name" to name, "notes" to notes), ActionTier.FREE)) {
                dao.saveProfile(UserProfile(name = name.trim(), notes = notes.trim()))
                ActionDispatchResult("Profile saved locally")
            }
            when (report.status) {
                ActionExecutionStatus.AWAITING_CONFIRMATION -> {
                    pendingAction.value = PendingShivAction.SaveProfile(name, notes)
                }
                ActionExecutionStatus.SUCCEEDED -> Unit
                else -> dao.addMessage(ChatMessage(text = executionFailure(report, "profile update"), fromUser = false))
            }
        }
    }

    fun adjustEvent(event: EventRecord, minutes: Int) {
        viewModelScope.launch {
            val updated = event.copy(startsAt = event.startsAt + minutes * 60_000L)
            val report = actionExecutor.execute(actionRequest(
                "RESCHEDULE",
                mapOf("eventId" to event.id, "startsAt" to event.startsAt.toString(), "newStartsAt" to updated.startsAt.toString()),
                ActionTier.ACT_THEN_TELL
            )) {
                    dao.saveEvent(updated)
                    reminders.cancel(event)
                    reminders.schedule(updated, reminderLeadMinutes.value)
                    ActionDispatchResult("Event and reminder updated", "RESCHEDULE:${event.id}:${event.startsAt}")
                }
            when (report.status) {
                ActionExecutionStatus.AWAITING_CONFIRMATION -> pendingAction.value = PendingShivAction.Reschedule(event, updated.startsAt)
                ActionExecutionStatus.SUCCEEDED -> dao.addMessage(ChatMessage(text = "Done. ${event.title} moved by $minutes minutes, and I reset its reminder.", fromUser = false, tier = report.authorization.effectiveTier.name))
                else -> dao.addMessage(ChatMessage(text = executionFailure(report, "reschedule"), fromUser = false))
            }
        }
    }

    fun askDeleteEvent(event: EventRecord) {
        viewModelScope.launch {
            val report = actionExecutor.execute(actionRequest("DELETE_DATA", mapOf("eventId" to event.id), ActionTier.ASK_FIRST)) {
                ActionDispatchResult("Waiting for deletion confirmation")
            }
            if (report.status == ActionExecutionStatus.AWAITING_CONFIRMATION) pendingAction.value = PendingShivAction.DeleteEvent(event)
            else if (report.status != ActionExecutionStatus.SUCCEEDED) dao.addMessage(ChatMessage(text = executionFailure(report, "event deletion"), fromUser = false))
        }
    }

    fun askDeleteMemory(memory: MemoryFact) {
        viewModelScope.launch {
            val report = actionExecutor.execute(actionRequest("DELETE_DATA", mapOf("memoryId" to memory.id), ActionTier.ASK_FIRST)) {
                ActionDispatchResult("Waiting for deletion confirmation")
            }
            if (report.status == ActionExecutionStatus.AWAITING_CONFIRMATION) pendingAction.value = PendingShivAction.DeleteMemory(memory)
            else if (report.status != ActionExecutionStatus.SUCCEEDED) dao.addMessage(ChatMessage(text = executionFailure(report, "memory deletion"), fromUser = false))
        }
    }

    fun askDeleteEverything() {
        viewModelScope.launch {
            val report = actionExecutor.execute(actionRequest("DELETE_DATA", emptyMap(), ActionTier.ASK_FIRST)) {
                ActionDispatchResult("Waiting for deletion confirmation")
            }
            if (report.status == ActionExecutionStatus.AWAITING_CONFIRMATION) pendingAction.value = PendingShivAction.DeleteEverything
            else if (report.status != ActionExecutionStatus.SUCCEEDED) dao.addMessage(ChatMessage(text = executionFailure(report, "data deletion"), fromUser = false))
        }
    }

    fun confirmPendingEvent(candidate: PendingEventRecord) {
        viewModelScope.launch {
            if (candidate.startsAt <= System.currentTimeMillis()) {
                dao.deletePendingEvent(candidate.id)
                dao.logAction(ActionLogEntry(action = "Expired a listening event confirmation", tier = ActionTier.ASK_FIRST.name, outcome = "Event time passed"))
                return@launch
            }
            val report = actionExecutor.execute(actionRequest(
                "CREATE_EVENT",
                mapOf("id" to candidate.id, "title" to candidate.title, "startsAt" to candidate.startsAt.toString()),
                ActionTier.FREE,
                confirmed = true
            )) {
                val event = EventRecord(id = candidate.id, title = candidate.title, startsAt = candidate.startsAt, place = candidate.place, person = candidate.person, source = "LISTENING", createdAt = candidate.createdAt)
                dao.saveEvent(event)
                dao.deletePendingEvent(candidate.id)
                reminders.schedule(event, reminderLeadMinutes.value)
                ActionDispatchResult("Listening event saved", "DELETE_EVENT:${event.id}")
            }
            if (report.status != ActionExecutionStatus.SUCCEEDED) {
                dao.deletePendingEvent(candidate.id)
                dao.addMessage(ChatMessage(text = executionFailure(report, "listening event"), fromUser = false))
            }
        }
    }

    fun ignorePendingEvent(candidate: PendingEventRecord) {
        viewModelScope.launch {
            dao.deletePendingEvent(candidate.id)
            dao.logAction(ActionLogEntry(action = "Ignored a listening event candidate", tier = ActionTier.ASK_FIRST.name, outcome = "Not saved"))
        }
    }

    fun cancelPending() {
        val action = pendingAction.value ?: return
        pendingAction.value = null
        viewModelScope.launch { dao.logAction(ActionLogEntry(action = action.title, tier = ActionTier.ASK_FIRST.name, outcome = "Cancelled by user")) }
    }

    /** Final authorization, audit, and side effect all run after the owner confirms. */
    fun confirmPending(onSmsDraft: (SmsDraft) -> Unit = {}) {
        val action = pendingAction.value ?: return
        viewModelScope.launch {
            val (type, args, tier) = pendingActionRequest(action)
            val report = actionExecutor.execute(actionRequest(type, args, tier, confirmed = true)) {
                when (action) {
                    is PendingShivAction.SaveProfile -> dao.saveProfile(UserProfile(name = action.name.trim(), notes = action.notes.trim()))
                    is PendingShivAction.SaveEvent -> saveEvent(action.event)
                    is PendingShivAction.SaveMemory -> dao.saveMemory(action.memory)
                    is PendingShivAction.SaveRule -> updateReminderLead(action.minutes)
                    is PendingShivAction.Reschedule -> {
                        val changed = action.event.copy(startsAt = action.newStart)
                        reminders.cancel(action.event)
                        saveEvent(changed)
                    }
                    is PendingShivAction.SaveDraft -> dao.saveDraft(action.draft)
                    is PendingShivAction.SendSms -> onSmsDraft(action.draft)
                    is PendingShivAction.DeleteEvent -> {
                        reminders.cancel(action.event)
                        dao.deleteEvent(action.event.id)
                    }
                    is PendingShivAction.DeleteMemory -> dao.deleteMemory(action.memory.id)
                    PendingShivAction.DeleteEverything -> {
                        dao.upcomingEvents(0).forEach { reminders.cancel(it) }
                        dao.clearMessages(); dao.clearProfile(); dao.clearMemories(); dao.clearEvents(); dao.clearLog(); dao.clearRules(); dao.clearDrafts(); dao.clearActionTiers()
                        dao.clearPendingEvents()
                        dao.clearSettings(); dao.saveSettings(ShivSettings())
                        reminderLeadMinutes.value = Brain.DEFAULT_LEAD_MINUTES
                    }
                }
                ActionDispatchResult(
                    result = if (action is PendingShivAction.SendSms) "Message compose opened; user must still tap Send" else "Confirmed action completed",
                    undoInfo = when (action) {
                        is PendingShivAction.SaveEvent -> "DELETE_EVENT:${action.event.id}"
                        is PendingShivAction.SaveMemory -> "DELETE_MEMORY:${action.memory.id}"
                        is PendingShivAction.DeleteEvent -> "RESTORE_EVENT:${action.event.id}"
                        is PendingShivAction.DeleteMemory -> "RESTORE_MEMORY:${action.memory.id}"
                        else -> null
                    }
                )
            }
            if (report.dispatched) pendingAction.value = null
            if (report.status != ActionExecutionStatus.SUCCEEDED) {
                dao.addMessage(ChatMessage(text = executionFailure(report, action.title.lowercase()), fromUser = false, tier = report.authorization.effectiveTier.name))
            }
        }
    }

    private fun pendingActionRequest(action: PendingShivAction): Triple<String, Map<String, String>, ActionTier> = when (action) {
        is PendingShivAction.SaveProfile -> Triple("SAVE_PROFILE", mapOf("name" to action.name, "notes" to action.notes), ActionTier.FREE)
        is PendingShivAction.SaveEvent -> Triple("CREATE_EVENT", mapOf("id" to action.event.id, "title" to action.event.title, "startsAt" to action.event.startsAt.toString()), ActionTier.FREE)
        is PendingShivAction.SaveMemory -> Triple("SAVE_MEMORY", mapOf("id" to action.memory.id, "text" to action.memory.text), ActionTier.FREE)
        is PendingShivAction.SaveRule -> Triple("SET_REMINDER", mapOf("minutes" to action.minutes.toString()), ActionTier.FREE)
        is PendingShivAction.Reschedule -> Triple("RESCHEDULE", mapOf("eventId" to action.event.id, "startsAt" to action.event.startsAt.toString(), "newStartsAt" to action.newStart.toString()), ActionTier.ACT_THEN_TELL)
        is PendingShivAction.SaveDraft -> Triple("DRAFT_REPLY", mapOf("person" to action.draft.person, "body" to action.draft.body), ActionTier.ACT_THEN_TELL)
        is PendingShivAction.SendSms -> Triple("SEND_MESSAGE", mapOf("person" to action.draft.person, "body" to action.draft.body), ActionTier.ASK_FIRST)
        is PendingShivAction.DeleteEvent -> Triple("DELETE_DATA", mapOf("eventId" to action.event.id), ActionTier.ASK_FIRST)
        is PendingShivAction.DeleteMemory -> Triple("DELETE_DATA", mapOf("memoryId" to action.memory.id), ActionTier.ASK_FIRST)
        PendingShivAction.DeleteEverything -> Triple("DELETE_DATA", emptyMap(), ActionTier.ASK_FIRST)
    }

    fun syncCalendar(context: Context) {
        viewModelScope.launch {
            val result = calendarSync.synchronize()
            lastCalendarSync.value = when {
                result.permissionDenied -> "Calendar permission is off. Grant access before syncing."
                result.failed -> "Calendar sync failed. Your saved events were left unchanged."
                else -> "Synced ${result.importedCount} upcoming calendar events on this phone."
            }
            ProactiveWorker.checkSoon(context.applicationContext)
        }
    }

    fun appendAssistant(text: String) {
        viewModelScope.launch {
            dao.addMessage(ChatMessage(text = text, fromUser = false, tier = ActionTier.FREE.name))
            dao.logAction(ActionLogEntry(action = "Analyzed a photo on this phone", tier = ActionTier.FREE.name, outcome = "Photo not saved"))
        }
    }
}
