package app.shiv.data.repository

import app.shiv.data.local.*

import android.content.Context
import app.shiv.data.mapping.RoomRecordMappers
import app.shiv.domain.model.*
import app.shiv.domain.repository.ShivViewRepository
import app.shiv.domain.integration.CalendarImportRepository
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Maps pure domain records at the Room boundary. */
class RoomShivViewRepository internal constructor(private val database: ShivDatabase) : ShivViewRepository {
    private val dao = database.dao()
    constructor(context: Context) : this(ShivDatabase.get(context.applicationContext))

    override fun messages(): Flow<List<ChatMessage>> = dao.messages().map { rows -> rows.map(RoomRecordMappers::toDomain) }
    override suspend fun addMessage(value: ChatMessage) = dao.addMessage(RoomRecordMappers.toRoom(value))
    override suspend fun saveProfile(value: UserProfile) = dao.saveProfile(RoomRecordMappers.toRoom(value))
    override fun profile(): Flow<UserProfile?> = dao.profile().map { it?.let(RoomRecordMappers::toDomain) }
    override suspend fun profileOnce(): UserProfile? = dao.profileOnce()?.let(RoomRecordMappers::toDomain)
    override fun memories(): Flow<List<MemoryFact>> = dao.memories().map { rows -> rows.map(RoomRecordMappers::toDomain) }
    override suspend fun saveMemory(value: MemoryFact) = dao.saveMemory(RoomRecordMappers.toRoom(value))
    override suspend fun deleteMemory(id: String) = dao.deleteMemory(id)
    override fun events(from: Long): Flow<List<EventRecord>> = dao.events(from).map { rows -> rows.map(RoomRecordMappers::toDomain) }
    override suspend fun upcomingEvents(from: Long): List<EventRecord> = dao.upcomingEvents(from).map(RoomRecordMappers::toDomain)
    override suspend fun saveEvent(value: EventRecord) = dao.saveEvent(RoomRecordMappers.toRoom(value))
    override suspend fun deleteEvent(id: String) = dao.deleteEvent(id)
    override fun pendingEvents(from: Long): Flow<List<PendingEventRecord>> = dao.pendingEvents(from).map { rows -> rows.map(RoomRecordMappers::toDomain) }
    override suspend fun savePendingEvent(value: PendingEventRecord) = dao.addPendingEvent(RoomRecordMappers.toRoom(value))
    override suspend fun deletePendingEvent(id: String) = dao.deletePendingEvent(id)
    override suspend fun calendarEvents(): List<EventRecord> = dao.calendarEvents().map(RoomRecordMappers::toDomain)
    override suspend fun clearCalendarEvents() = dao.clearCalendarEvents()
    override suspend fun replaceCalendarEvents(events: List<EventRecord>) {
        database.withTransaction {
            dao.clearCalendarEvents()
            events.forEach { dao.saveEvent(RoomRecordMappers.toRoom(it)) }
        }
    }
    override suspend fun recordCalendarImport(eventCount: Int) = dao.logAction(
        RoomRecordMappers.toRoom(ActionLogEntry(action = "Synced phone calendar", tier = "FREE", outcome = "$eventCount events saved locally"))
    )
    override suspend fun logAction(value: ActionLogEntry) = dao.logAction(RoomRecordMappers.toRoom(value))
    override suspend fun reminderRule(key: String): ReminderRule? = dao.reminderRule(key)?.let(RoomRecordMappers::toDomain)
    override suspend fun saveRule(value: ReminderRule) = dao.saveRule(RoomRecordMappers.toRoom(value))
    override fun actionLog(): Flow<List<ActionLogEntry>> = dao.actionLog().map { rows -> rows.map(RoomRecordMappers::toDomain) }
    override fun drafts(): Flow<List<ReplyDraft>> = dao.drafts().map { rows -> rows.map(RoomRecordMappers::toDomain) }
    override suspend fun saveDraft(value: ReplyDraft) = dao.saveDraft(RoomRecordMappers.toRoom(value))
    override fun actionTiers(): Flow<List<ActionTierSetting>> = dao.actionTiers().map { rows -> rows.map(RoomRecordMappers::toDomain) }
    override suspend fun saveActionTier(value: ActionTierSetting) = dao.saveActionTier(RoomRecordMappers.toRoom(value))
    override fun settings(): Flow<ShivSettings?> = dao.settings().map { it?.let(RoomRecordMappers::toDomain) }
    override suspend fun settingsOnce(): ShivSettings? = dao.settingsOnce()?.let(RoomRecordMappers::toDomain)
    override suspend fun saveSettings(value: ShivSettings) = dao.saveSettings(RoomRecordMappers.toRoom(value))
    override suspend fun clearSettings() = dao.clearSettings()
    override suspend fun clearMessages() = dao.clearMessages()
    override suspend fun clearProfile() = dao.clearProfile()
    override suspend fun clearMemories() = dao.clearMemories()
    override suspend fun clearEvents() = dao.clearEvents()
    override suspend fun clearLog() = dao.clearLog()
    override suspend fun clearRules() = dao.clearRules()
    override suspend fun clearDrafts() = dao.clearDrafts()
    override suspend fun clearActionTiers() = dao.clearActionTiers()
    override suspend fun clearPendingEvents() = dao.clearPendingEvents()
    override fun tasks(): Flow<List<TaskRecord>> = dao.tasks().map { rows -> rows.map(RoomRecordMappers::toDomain) }
    override suspend fun task(id: String): TaskRecord? = dao.task(id)?.let(RoomRecordMappers::toDomain)
    override suspend fun saveTask(value: TaskRecord) = dao.saveTask(RoomRecordMappers.toRoom(value))
    override suspend fun deleteTask(id: String) = dao.deleteTask(id)
    override suspend fun saveReminder(value: ReminderRecord) = dao.saveReminder(RoomRecordMappers.toRoom(value))
    override suspend fun reminder(id: String): ReminderRecord? = dao.reminder(id)?.let(RoomRecordMappers::toDomain)
    override suspend fun remindersForEvent(eventId: String): List<ReminderRecord> = dao.remindersForEvent(eventId).map(RoomRecordMappers::toDomain)
    override suspend fun pendingReminders(limit: Int): List<ReminderRecord> = dao.pendingReminders(limit.coerceIn(1, 500)).map(RoomRecordMappers::toDomain)
    override suspend fun setReminderState(id: String, state: String, updatedAt: Long): Boolean = dao.setReminderState(id, state, updatedAt) == 1
    override suspend fun deleteReminder(id: String) = dao.deleteReminder(id)
    override fun modelMetadata(): Flow<List<ModelMetadata>> = dao.modelMetadata().map { rows -> rows.map(RoomRecordMappers::toDomain) }
    override suspend fun modelMetadata(role: String): ModelMetadata? = dao.modelMetadata(role)?.let(RoomRecordMappers::toDomain)
    override suspend fun saveModelMetadata(value: ModelMetadata) = dao.saveModelMetadata(RoomRecordMappers.toRoom(value))
    override suspend fun deleteModelMetadata(role: String) = dao.deleteModelMetadata(role)
    override suspend fun saveRecordSource(value: RecordSourceMetadata) {
        database.withTransaction {
            dao.deleteRecordSources(value.recordType, value.recordId)
            dao.saveRecordSource(RoomRecordMappers.toRoom(value))
        }
    }
    override suspend fun recordSources(recordType: String, recordId: String): List<RecordSourceMetadata> =
        dao.recordSources(recordType, recordId).map(RoomRecordMappers::toDomain)
    override suspend fun deleteRecordSources(recordType: String, recordId: String) = dao.deleteRecordSources(recordType, recordId)
    override suspend fun clearTasks() = dao.clearTasks()
    override suspend fun clearReminders() = dao.clearReminders()
    override suspend fun clearModelMetadata() = dao.clearModelMetadata()
    override suspend fun clearRecordSources() = dao.clearRecordSources()
    override suspend fun clearCaptures() = dao.clearCaptures()
}
