package app.shiv.ui

import app.shiv.domain.model.*
import app.shiv.domain.repository.ShivViewRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/** Defers SQLCipher, Keystore and database construction until an IO dispatcher is active. */
internal class AsyncShivViewRepository(private val create: () -> ShivViewRepository) : ShivViewRepository {
    private val delegate by lazy(LazyThreadSafetyMode.SYNCHRONIZED, create)

    private suspend fun <T> io(block: suspend ShivViewRepository.() -> T): T =
        withContext(Dispatchers.IO) { delegate.block() }

    private fun <T> stream(block: ShivViewRepository.() -> Flow<T>): Flow<T> = flow {
        emitAll(io { block() })
    }

    override fun messages() = stream { messages() }
    override suspend fun addMessage(value: ChatMessage) = io { addMessage(value) }
    override suspend fun saveProfile(value: UserProfile) = io { saveProfile(value) }
    override fun profile() = stream { profile() }
    override suspend fun profileOnce() = io { profileOnce() }
    override fun memories() = stream { memories() }
    override suspend fun saveMemory(value: MemoryFact) = io { saveMemory(value) }
    override suspend fun deleteMemory(id: String) = io { deleteMemory(id) }
    override fun events(from: Long) = stream { events(from) }
    override suspend fun upcomingEvents(from: Long) = io { upcomingEvents(from) }
    override suspend fun saveEvent(value: EventRecord) = io { saveEvent(value) }
    override suspend fun deleteEvent(id: String) = io { deleteEvent(id) }
    override fun pendingEvents(from: Long) = stream { pendingEvents(from) }
    override suspend fun savePendingEvent(value: PendingEventRecord) = io { savePendingEvent(value) }
    override suspend fun deletePendingEvent(id: String) = io { deletePendingEvent(id) }
    override suspend fun calendarEvents() = io { calendarEvents() }
    override suspend fun replaceCalendarEvents(events: List<EventRecord>) = io { replaceCalendarEvents(events) }
    override suspend fun recordCalendarImport(eventCount: Int) = io { recordCalendarImport(eventCount) }
    override suspend fun clearCalendarEvents() = io { clearCalendarEvents() }
    override suspend fun logAction(value: ActionLogEntry) = io { logAction(value) }
    override suspend fun reminderRule(key: String) = io { reminderRule(key) }
    override suspend fun saveRule(value: ReminderRule) = io { saveRule(value) }
    override fun actionLog() = stream { actionLog() }
    override fun drafts() = stream { drafts() }
    override suspend fun saveDraft(value: ReplyDraft) = io { saveDraft(value) }
    override fun actionTiers() = stream { actionTiers() }
    override suspend fun saveActionTier(value: ActionTierSetting) = io { saveActionTier(value) }
    override fun settings() = stream { settings() }
    override suspend fun settingsOnce() = io { settingsOnce() }
    override suspend fun saveSettings(value: ShivSettings) = io { saveSettings(value) }
    override suspend fun clearSettings() = io { clearSettings() }
    override suspend fun clearMessages() = io { clearMessages() }
    override suspend fun clearProfile() = io { clearProfile() }
    override suspend fun clearMemories() = io { clearMemories() }
    override suspend fun clearEvents() = io { clearEvents() }
    override suspend fun clearLog() = io { clearLog() }
    override suspend fun clearRules() = io { clearRules() }
    override suspend fun clearDrafts() = io { clearDrafts() }
    override suspend fun clearActionTiers() = io { clearActionTiers() }
    override suspend fun clearPendingEvents() = io { clearPendingEvents() }
    override fun tasks() = stream { tasks() }
    override suspend fun task(id: String) = io { task(id) }
    override suspend fun saveTask(value: TaskRecord) = io { saveTask(value) }
    override suspend fun deleteTask(id: String) = io { deleteTask(id) }
    override suspend fun saveReminder(value: ReminderRecord) = io { saveReminder(value) }
    override suspend fun reminder(id: String) = io { reminder(id) }
    override suspend fun remindersForEvent(eventId: String) = io { remindersForEvent(eventId) }
    override suspend fun pendingReminders(limit: Int) = io { pendingReminders(limit) }
    override suspend fun setReminderState(id: String, state: String, updatedAt: Long) = io { setReminderState(id, state, updatedAt) }
    override suspend fun deleteReminder(id: String) = io { deleteReminder(id) }
    override fun modelMetadata() = stream { modelMetadata() }
    override suspend fun modelMetadata(role: String) = io { modelMetadata(role) }
    override suspend fun saveModelMetadata(value: ModelMetadata) = io { saveModelMetadata(value) }
    override suspend fun deleteModelMetadata(role: String) = io { deleteModelMetadata(role) }
    override suspend fun saveRecordSource(value: RecordSourceMetadata) = io { saveRecordSource(value) }
    override suspend fun recordSources(recordType: String, recordId: String) = io { recordSources(recordType, recordId) }
    override suspend fun deleteRecordSources(recordType: String, recordId: String) = io { deleteRecordSources(recordType, recordId) }
    override suspend fun clearTasks() = io { clearTasks() }
    override suspend fun clearReminders() = io { clearReminders() }
    override suspend fun clearModelMetadata() = io { clearModelMetadata() }
    override suspend fun clearRecordSources() = io { clearRecordSources() }
    override suspend fun clearCaptures() = io { clearCaptures() }
}
