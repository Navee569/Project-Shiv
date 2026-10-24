package app.shiv.domain.repository

import app.shiv.ActionLogEntry
import app.shiv.ActionTierSetting
import app.shiv.ChatMessage
import app.shiv.EventRecord
import app.shiv.MemoryFact
import app.shiv.PendingEventRecord
import app.shiv.ReminderRule
import app.shiv.ReplyDraft
import app.shiv.ShivSettings
import app.shiv.UserProfile
import kotlinx.coroutines.flow.Flow

/** Persistence capabilities required by the app's presentation layer. */
interface ShivViewRepository {
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
    suspend fun deletePendingEvent(id: String)
    suspend fun calendarEvents(): List<EventRecord>
    suspend fun clearCalendarEvents()
    suspend fun logAction(value: ActionLogEntry)
    suspend fun reminderRule(key: String): ReminderRule?
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
}
