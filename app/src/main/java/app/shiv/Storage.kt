package app.shiv

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.util.UUID

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val text: String,
    val fromUser: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    val tier: String? = null
)

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "memories", indices = [Index("createdAt")])
data class MemoryFact(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "events", indices = [Index("startsAt"), Index("source")])
data class EventRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val startsAt: Long,
    val place: String? = null,
    val person: String? = null,
    val source: String = "CHAT",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "action_log", indices = [Index("createdAt")])
data class ActionLogEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val action: String,
    val tier: String,
    val outcome: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reminder_rules")
data class ReminderRule(@PrimaryKey val ruleKey: String, val value: String, val updatedAt: Long = System.currentTimeMillis())

@Entity(tableName = "reply_drafts", indices = [Index("createdAt")])
data class ReplyDraft(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val person: String,
    val body: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface ShivDao {
    @Query("SELECT * FROM chat_messages ORDER BY createdAt") fun messages(): Flow<List<ChatMessage>>
    @Insert suspend fun addMessage(value: ChatMessage)
    @Upsert suspend fun saveProfile(value: UserProfile)
    @Query("SELECT * FROM user_profile WHERE id = 1") fun profile(): Flow<UserProfile?>
    @Query("SELECT * FROM user_profile WHERE id = 1") suspend fun profileOnce(): UserProfile?
    @Query("SELECT * FROM memories ORDER BY createdAt DESC") fun memories(): Flow<List<MemoryFact>>
    @Query("SELECT * FROM memories ORDER BY createdAt DESC LIMIT 100") suspend fun latestMemories(): List<MemoryFact>
    @Upsert suspend fun saveMemory(value: MemoryFact)
    @Query("DELETE FROM memories WHERE id = :id") suspend fun deleteMemory(id: String)
    @Query("SELECT * FROM events WHERE startsAt >= :from ORDER BY startsAt") fun events(from: Long): Flow<List<EventRecord>>
    @Query("SELECT * FROM events WHERE startsAt >= :from ORDER BY startsAt") suspend fun upcomingEvents(from: Long): List<EventRecord>
    @Query("SELECT * FROM events WHERE source = 'CALENDAR'") suspend fun calendarEvents(): List<EventRecord>
    @Upsert suspend fun saveEvent(value: EventRecord)
    @Query("DELETE FROM events WHERE id = :id") suspend fun deleteEvent(id: String)
    @Query("DELETE FROM events WHERE source = 'CALENDAR'") suspend fun clearCalendarEvents()
    @Insert suspend fun logAction(value: ActionLogEntry)
    @Query("SELECT * FROM action_log ORDER BY createdAt DESC LIMIT 200") fun actionLog(): Flow<List<ActionLogEntry>>
    @Query("SELECT * FROM reminder_rules WHERE ruleKey = :key") suspend fun reminderRule(key: String): ReminderRule?
    @Upsert suspend fun saveRule(value: ReminderRule)
    @Query("SELECT * FROM reply_drafts ORDER BY createdAt DESC") fun drafts(): Flow<List<ReplyDraft>>
    @Insert suspend fun saveDraft(value: ReplyDraft)
    @Query("DELETE FROM chat_messages") suspend fun clearMessages()
    @Query("DELETE FROM user_profile") suspend fun clearProfile()
    @Query("DELETE FROM memories") suspend fun clearMemories()
    @Query("DELETE FROM events") suspend fun clearEvents()
    @Query("DELETE FROM action_log") suspend fun clearLog()
    @Query("DELETE FROM reminder_rules") suspend fun clearRules()
    @Query("DELETE FROM reply_drafts") suspend fun clearDrafts()
}

@Database(
    entities = [ChatMessage::class, UserProfile::class, MemoryFact::class, EventRecord::class, ActionLogEntry::class, ReminderRule::class, ReplyDraft::class],
    version = 1,
    exportSchema = true
)
abstract class ShivDatabase : RoomDatabase() {
    abstract fun dao(): ShivDao

    companion object {
        @Volatile private var instance: ShivDatabase? = null
        fun get(context: Context): ShivDatabase = instance ?: synchronized(this) {
            instance ?: run {
                System.loadLibrary("sqlcipher")
                val key = LocalKeyStore(context).databaseKey()
                val factory = SupportOpenHelperFactory(key)
                Room.databaseBuilder(context, ShivDatabase::class.java, "shiv-memory.db")
                    .openHelperFactory(factory)
                    .build()
                    .also { instance = it }
            }
        }
    }
}

class ShivRepository(context: Context) {
    val dao: ShivDao = ShivDatabase.get(context.applicationContext).dao()
}
