package app.shiv.domain.repository

import app.shiv.domain.model.*
import kotlinx.coroutines.flow.Flow
import app.shiv.domain.integration.CalendarImportRepository

/** Persistence capabilities required by the app's presentation layer. */
interface ShivViewRepository : CalendarImportRepository {
    fun messages(): Flow<List<ChatMessage>>
    suspend fun addMessage(value: ChatMessage)
    suspend fun saveProfile(value: UserProfile)
    fun profile(): Flow<UserProfile?>
    suspend fun profileOnce(): UserProfile?
    fun memories(): Flow<List<MemoryFact>>
    suspend fun saveMemory(value: MemoryFact)
    suspend fun deleteMemory(id: String)
    fun events(from: Long): Flow<List<EventRecord>>
    suspend fun upcomingEvents(from: Long): List<EventRecord>
    suspend fun saveEvent(value: EventRecord)
    suspend fun deleteEvent(id: String)
    fun pendingEvents(from: Long): Flow<List<PendingEventRecord>>
    suspend fun savePendingEvent(value: PendingEventRecord)
    suspend fun deletePendingEvent(id: String)
    override suspend fun calendarEvents(): List<EventRecord>
    suspend fun clearCalendarEvents()
    suspend fun logAction(value: ActionLogEntry)
    override suspend fun reminderRule(key: String): ReminderRule?
    suspend fun saveRule(value: ReminderRule)
    fun actionLog(): Flow<List<ActionLogEntry>>
    fun drafts(): Flow<List<ReplyDraft>>
    suspend fun saveDraft(value: ReplyDraft)
    fun actionTiers(): Flow<List<ActionTierSetting>>
    suspend fun saveActionTier(value: ActionTierSetting)
    fun settings(): Flow<ShivSettings?>
    suspend fun settingsOnce(): ShivSettings?
    suspend fun saveSettings(value: ShivSettings)
    suspend fun clearSettings()
    suspend fun clearMessages()
    suspend fun clearProfile()
    suspend fun clearMemories()
    suspend fun clearEvents()
    suspend fun clearLog()
    suspend fun clearRules()
    suspend fun clearDrafts()
    suspend fun clearActionTiers()
    suspend fun clearPendingEvents()
    fun tasks(): Flow<List<TaskRecord>>
    suspend fun task(id: String): TaskRecord?
    suspend fun saveTask(value: TaskRecord)
    suspend fun deleteTask(id: String)
    suspend fun saveReminder(value: ReminderRecord)
    suspend fun reminder(id: String): ReminderRecord?
    suspend fun remindersForEvent(eventId: String): List<ReminderRecord>
    suspend fun pendingReminders(limit: Int): List<ReminderRecord>
    suspend fun setReminderState(id: String, state: String, updatedAt: Long): Boolean
    suspend fun deleteReminder(id: String)
    fun modelMetadata(): Flow<List<ModelMetadata>>
    suspend fun modelMetadata(role: String): ModelMetadata?
    suspend fun saveModelMetadata(value: ModelMetadata)
    suspend fun deleteModelMetadata(role: String)
    suspend fun saveRecordSource(value: RecordSourceMetadata)
    suspend fun recordSources(recordType: String, recordId: String): List<RecordSourceMetadata>
    suspend fun deleteRecordSources(recordType: String, recordId: String)
    suspend fun clearTasks()
    suspend fun clearReminders()
    suspend fun clearModelMetadata()
    suspend fun clearRecordSources()
    suspend fun clearCaptures()
}
