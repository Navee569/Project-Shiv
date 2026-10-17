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
    class DeleteEvent(val event: EventRecord) : PendingShivAction("Delete event?", "This will remove ${event.title} from Shiv's encrypted memory.")
    class DeleteMemory(val memory: MemoryFact) : PendingShivAction("Forget this memory?", "Shiv will remove the saved fact: ${memory.text}")
    object DeleteEverything : PendingShivAction("Erase all Shiv data?", "This permanently deletes your local chats, profile, memories, events, drafts, rules, and action history.")
    class SendSms(val draft: SmsDraft) : PendingShivAction("Open a message draft?", "Shiv will open your messaging app with the text prepared for ${draft.person}. It will not send until you tap Send in that app.")
}

class ShivViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = ShivRepository(app).dao
    private val brain = Brain()
    val messages = dao.messages().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val events = dao.events(System.currentTimeMillis()).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val memories = dao.memories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val profile = dao.profile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val actionLog = dao.actionLog().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val drafts = dao.drafts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val reminderLeadMinutes = MutableStateFlow(Brain.DEFAULT_LEAD_MINUTES)
    val pendingAction = MutableStateFlow<PendingShivAction?>(null)
    val lastCalendarSync = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            reminderLeadMinutes.value = dao.reminderRule(Brain.REMINDER_LEAD_KEY)?.value?.toIntOrNull() ?: Brain.DEFAULT_LEAD_MINUTES
            if (dao.profileOnce() == null) dao.saveProfile(UserProfile())
        }
    }

    fun send(text: String) {
        val input = text.trim()
        if (input.isBlank()) return
        viewModelScope.launch {
            dao.addMessage(ChatMessage(text = input, fromUser = true))
            val parsed = brain.answer(input, profile.value, memories.value, reminderLeadMinutes.value)
            parsed.memory?.let {
                if (memories.value.none { old -> old.text.equals(it.text, ignoreCase = true) }) {
                    dao.saveMemory(it)
                    dao.logAction(ActionLogEntry(action = "Saved a personal memory", tier = ActionTier.FREE.name, outcome = it.text))
                }
            }
            parsed.reminderLeadMinutes?.let { updateReminderLead(it) }
            parsed.event?.let { event ->
                dao.saveEvent(event)
                AlarmScheduler.schedule(getApplication(), event, reminderLeadMinutes.value)
                dao.logAction(ActionLogEntry(action = "Saved an event and set its reminder", tier = ActionTier.FREE.name, outcome = event.title))
            }
            var answer = parsed.text
            parsed.reschedule?.let { request ->
                val match = events.value.filter { it.startsAt > System.currentTimeMillis() }
                    .firstOrNull { it.title.contains(request.search, true) || it.person?.contains(request.search, true) == true || request.search.contains(it.title, true) }
                if (match == null) answer = "I couldn’t find an upcoming event matching ‘${request.search}’. Nothing was changed."
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
                dao.saveDraft(it)
                dao.logAction(ActionLogEntry(action = "Prepared a reply draft for ${it.person}", tier = ActionTier.ACT_THEN_TELL.name, outcome = "Draft saved; not sent"))
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
            dao.addMessage(ChatMessage(text = answer, fromUser = false, tier = tierFor(parsed).name))
        }
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
        viewModelScope.launch(Dispatchers.IO) {
            dao.saveProfile(UserProfile(name = name.trim(), notes = notes.trim()))
            dao.logAction(ActionLogEntry(action = "Updated personal profile", tier = ActionTier.FREE.name, outcome = "Saved on this phone"))
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
                    dao.clearMessages(); dao.clearProfile(); dao.clearMemories(); dao.clearEvents(); dao.clearLog(); dao.clearRules(); dao.clearDrafts()
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
        }
    }

    fun appendAssistant(text: String) {
        viewModelScope.launch {
            dao.addMessage(ChatMessage(text = text, fromUser = false, tier = ActionTier.FREE.name))
            dao.logAction(ActionLogEntry(action = "Analyzed a photo on this phone", tier = ActionTier.FREE.name, outcome = "Photo not saved"))
        }
    }
}
