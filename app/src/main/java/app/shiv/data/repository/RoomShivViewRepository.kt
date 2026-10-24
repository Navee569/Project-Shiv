package app.shiv.data.repository

import android.content.Context
import app.shiv.ShivDao
import app.shiv.ShivDatabase
import app.shiv.data.mapping.RoomRecordMappers
import app.shiv.domain.model.*
import app.shiv.domain.repository.ShivViewRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Maps pure domain records at the Room boundary. */
class RoomShivViewRepository internal constructor(private val dao: ShivDao) : ShivViewRepository {
    constructor(context: Context) : this(ShivDatabase.get(context.applicationContext).dao())

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
}
