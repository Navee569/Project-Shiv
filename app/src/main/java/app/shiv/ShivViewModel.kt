package app.shiv

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    private val dao = ShivRepository(app).dao
    private val brain = BrainProvider.get(app.applicationContext)
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
            runCatching { brain.importModel(uri, role) }
                .onSuccess {
                    refreshModelStatus()
                    modelStatus.value = "${role.replaceFirstChar(Char::uppercase)} model installed on this phone. It has not been uploaded."
                    dao.logAction(ActionLogEntry(action = "Imported a local language model", tier = ActionTier.FREE.name, outcome = role))
                }
                .onFailure { modelStatus.value = "Model import failed: ${it.message ?: it.javaClass.simpleName}" }
        }
    }

    private suspend fun refreshModelStatus() {
        modelFiles.value = brain.installedModels()
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
                    oldEvents.forEach { AlarmScheduler.cancel(getApplication(), it) }
                    val lead = dao.reminderRule(Brain.REMINDER_LEAD_KEY)?.value?.toIntOrNull() ?: Brain.DEFAULT_LEAD_MINUTES
                    dao.upcomingEvents(System.currentTimeMillis()).forEach { AlarmScheduler.schedule(getApplication(), it, lead) }
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
                    if (configuredTier("SAVE_MEMORY", ActionTier.FREE) == ActionTier.ASK_FIRST) {
                        pendingAction.value = PendingShivAction.SaveMemory(it)
                        answer = "I found a personal fact. I’ll save it only after you confirm."
                    } else {
                        dao.saveMemory(it)
                        dao.logAction(ActionLogEntry(action = "Saved a personal memory", tier = configuredTier("SAVE_MEMORY", ActionTier.FREE).name, outcome = it.text))
                    }
                }
            }
            parsed.reminderLeadMinutes?.let {
                if (configuredTier("SET_REMINDER", ActionTier.FREE) == ActionTier.ASK_FIRST) {
                    pendingAction.value = PendingShivAction.SaveRule(it)
                    answer = "I can change that reminder rule. Confirm and I’ll save it."
                } else updateReminderLead(it)
            }
            parsed.event?.let { event ->
                if (configuredTier("CREATE_EVENT", ActionTier.FREE) == ActionTier.ASK_FIRST) {
                    pendingAction.value = PendingShivAction.SaveEvent(event)
                    answer = "I found an event to save. Confirm and I’ll add it and set the reminder."
                } else saveEvent(event, configuredTier("CREATE_EVENT", ActionTier.FREE))
            }
            parsed.reschedule?.let { request ->
                val match = events.value.filter { it.startsAt > System.currentTimeMillis() }
                    .firstOrNull { it.id == request.search || it.title.contains(request.search, true) || it.person?.contains(request.search, true) == true || request.search.contains(it.title, true) }
                if (match == null) answer = "I couldn’t find an upcoming event matching ‘${request.search}’. Nothing was changed."
                else if (configuredTier("RESCHEDULE", ActionTier.ACT_THEN_TELL) == ActionTier.ASK_FIRST) {
                    pendingAction.value = PendingShivAction.Reschedule(match, request.newStart)
                    answer = "I can move ${match.title}. Confirm and I’ll update the event and reminder."
                }
                else {
                    val changed = match.copy(startsAt = request.newStart)
                    dao.saveEvent(changed)
                    AlarmScheduler.cancel(getApplication(), match)
                    AlarmScheduler.schedule(getApplication(), changed, reminderLeadMinutes.value)
                    dao.logAction(ActionLogEntry(action = "Rescheduled ${match.title}", tier = ActionTier.ACT_THEN_TELL.name, outcome = "Updated and reminder reset"))
                    answer = "Moved ${match.title} to ${android.text.format.DateFormat.format("EEE, MMM d 'at' h:mm a", changed.startsAt)}. Its reminder is reset."
                }
            }
            parsed.draft?.let {
                if (configuredTier("DRAFT_REPLY", ActionTier.ACT_THEN_TELL) == ActionTier.ASK_FIRST) {
                    pendingAction.value = PendingShivAction.SaveDraft(it)
                    answer = "I prepared the reply. Confirm and I’ll save it to your drafts."
                } else {
                    dao.saveDraft(it)
                    dao.logAction(ActionLogEntry(action = "Prepared a reply draft for ${it.person}", tier = configuredTier("DRAFT_REPLY", ActionTier.ACT_THEN_TELL).name, outcome = "Draft saved; not sent"))
                }
            }
            parsed.sms?.let {
                pendingAction.value = PendingShivAction.SendSms(it)
                dao.logAction(ActionLogEntry(action = "Requested opening a message draft for ${it.person}", tier = ActionTier.ASK_FIRST.name, outcome = "Waiting for confirmation"))
            }
            parsed.deleteEverything.let { if (it) {
                pendingAction.value = PendingShivAction.DeleteEverything
                dao.logAction(ActionLogEntry(action = "Requested erase of all Shiv data", tier = ActionTier.ASK_FIRST.name, outcome = "Waiting for confirmation"))
            } }
            parsed.forgetPhrase?.let { phrase ->
                val target = memories.value.firstOrNull { it.text.contains(phrase, true) || phrase.contains(it.text, true) }
                if (target != null) {
                    pendingAction.value = PendingShivAction.DeleteMemory(target)
                    dao.logAction(ActionLogEntry(action = "Requested forgetting a saved fact", tier = ActionTier.ASK_FIRST.name, outcome = "Waiting for confirmation"))
                } else answer = "I couldn’t find a saved memory matching that, so nothing was removed."
            }
            if (answer.isNotBlank()) dao.addMessage(ChatMessage(text = answer, fromUser = false, tier = tierFor(parsed).name))
        }
    }

    private fun configuredTier(key: String, minimum: ActionTier): ActionTier {
        val saved = actionTiers.value.firstOrNull { it.actionKey == key }?.tier?.let { runCatching { ActionTier.valueOf(it) }.getOrNull() } ?: minimum
        return if (saved.ordinal < minimum.ordinal) minimum else saved
    }

    private suspend fun saveEvent(event: EventRecord, tier: ActionTier) {
        dao.saveEvent(event)
        AlarmScheduler.schedule(getApplication(), event, reminderLeadMinutes.value)
        dao.logAction(ActionLogEntry(action = "Saved an event and set its reminder", tier = tier.name, outcome = event.title))
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
        dao.upcomingEvents(System.currentTimeMillis()).forEach { AlarmScheduler.schedule(getApplication(), it, safe) }
        dao.logAction(ActionLogEntry(action = "Updated the reminder lead-time rule", tier = ActionTier.FREE.name, outcome = "$safe minutes before each event"))
    }

    fun saveProfile(name: String, notes: String) {
        viewModelScope.launch {
            val tier = configuredTier("SAVE_PROFILE", ActionTier.FREE)
            if (tier == ActionTier.ASK_FIRST) {
                pendingAction.value = PendingShivAction.SaveProfile(name, notes)
                return@launch
            }
            dao.saveProfile(UserProfile(name = name.trim(), notes = notes.trim()))
            dao.logAction(ActionLogEntry(action = "Updated personal profile", tier = tier.name, outcome = "Saved on this phone"))
        }
    }

    fun adjustEvent(event: EventRecord, minutes: Int) {
        viewModelScope.launch {
            val updated = event.copy(startsAt = event.startsAt + minutes * 60_000L)
            dao.saveEvent(updated)
            AlarmScheduler.cancel(getApplication(), event)
            AlarmScheduler.schedule(getApplication(), updated, reminderLeadMinutes.value)
            dao.logAction(ActionLogEntry(action = "Rescheduled ${event.title} by $minutes minutes", tier = ActionTier.ACT_THEN_TELL.name, outcome = "Event and reminder updated"))
            dao.addMessage(ChatMessage(text = "Done. ${event.title} moved by $minutes minutes, and I reset its reminder.", fromUser = false, tier = ActionTier.ACT_THEN_TELL.name))
        }
    }

    fun askDeleteEvent(event: EventRecord) { pendingAction.value = PendingShivAction.DeleteEvent(event) }
    fun askDeleteMemory(memory: MemoryFact) { pendingAction.value = PendingShivAction.DeleteMemory(memory) }
    fun askDeleteEverything() { pendingAction.value = PendingShivAction.DeleteEverything }

    fun confirmPendingEvent(candidate: PendingEventRecord) {
        viewModelScope.launch {
            if (candidate.startsAt <= System.currentTimeMillis()) {
                dao.deletePendingEvent(candidate.id)
                dao.logAction(ActionLogEntry(action = "Expired a listening event confirmation", tier = ActionTier.ASK_FIRST.name, outcome = "Event time passed"))
                return@launch
            }
            val event = EventRecord(id = candidate.id, title = candidate.title, startsAt = candidate.startsAt, place = candidate.place, person = candidate.person, source = "LISTENING", createdAt = candidate.createdAt)
            dao.saveEvent(event)
            dao.deletePendingEvent(candidate.id)
            AlarmScheduler.schedule(getApplication(), event, reminderLeadMinutes.value)
            dao.logAction(ActionLogEntry(action = "Saved a listening event after confirmation", tier = ActionTier.ASK_FIRST.name, outcome = event.title))
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

    /** Returns a message draft only after the user taps the in-app confirm button. */
    fun confirmPending(): SmsDraft? {
        val action = pendingAction.value ?: return null
        pendingAction.value = null
        return when (action) {
            is PendingShivAction.SaveProfile -> { viewModelScope.launch { dao.saveProfile(UserProfile(name = action.name.trim(), notes = action.notes.trim())); dao.logAction(ActionLogEntry(action = "Updated personal profile", tier = ActionTier.ASK_FIRST.name, outcome = "Confirmed by user")) }; null }
            is PendingShivAction.SaveEvent -> { viewModelScope.launch { saveEvent(action.event, ActionTier.ASK_FIRST) }; null }
            is PendingShivAction.SaveMemory -> { viewModelScope.launch { dao.saveMemory(action.memory); dao.logAction(ActionLogEntry(action = "Saved a personal memory", tier = ActionTier.ASK_FIRST.name, outcome = action.memory.text)) }; null }
            is PendingShivAction.SaveRule -> { viewModelScope.launch { updateReminderLead(action.minutes); dao.logAction(ActionLogEntry(action = "Confirmed reminder rule change", tier = ActionTier.ASK_FIRST.name, outcome = "${action.minutes} minutes")) }; null }
            is PendingShivAction.Reschedule -> { viewModelScope.launch {
                val changed = action.event.copy(startsAt = action.newStart)
                AlarmScheduler.cancel(getApplication(), action.event); saveEvent(changed, ActionTier.ASK_FIRST)
                dao.logAction(ActionLogEntry(action = "Confirmed reschedule", tier = ActionTier.ASK_FIRST.name, outcome = changed.title))
            }; null }
            is PendingShivAction.SaveDraft -> { viewModelScope.launch { dao.saveDraft(action.draft); dao.logAction(ActionLogEntry(action = "Saved reply draft", tier = ActionTier.ASK_FIRST.name, outcome = action.draft.person)) }; null }
            is PendingShivAction.SendSms -> {
                viewModelScope.launch { dao.logAction(ActionLogEntry(action = "Opened the messaging app with a prepared draft", tier = ActionTier.ASK_FIRST.name, outcome = "User must still tap Send there")) }
                action.draft
            }
            is PendingShivAction.DeleteEvent -> {
                viewModelScope.launch { AlarmScheduler.cancel(getApplication(), action.event); dao.deleteEvent(action.event.id); dao.logAction(ActionLogEntry(action = "Deleted event ${action.event.title}", tier = ActionTier.ASK_FIRST.name, outcome = "Confirmed by user")) }
                null
            }
            is PendingShivAction.DeleteMemory -> {
                viewModelScope.launch { dao.deleteMemory(action.memory.id); dao.logAction(ActionLogEntry(action = "Forgot saved memory", tier = ActionTier.ASK_FIRST.name, outcome = "Confirmed by user")) }
                null
            }
            PendingShivAction.DeleteEverything -> {
                viewModelScope.launch {
                    dao.upcomingEvents(0).forEach { AlarmScheduler.cancel(getApplication(), it) }
                    dao.clearMessages(); dao.clearProfile(); dao.clearMemories(); dao.clearEvents(); dao.clearLog(); dao.clearRules(); dao.clearDrafts(); dao.clearActionTiers()
                    dao.clearPendingEvents()
                    dao.clearSettings(); dao.saveSettings(ShivSettings())
                    reminderLeadMinutes.value = Brain.DEFAULT_LEAD_MINUTES
                }
                null
            }
        }
    }

    fun syncCalendar(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val count = CalendarSyncer.sync(context.applicationContext, dao)
            lastCalendarSync.value = "Synced $count upcoming calendar events on this phone."
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
