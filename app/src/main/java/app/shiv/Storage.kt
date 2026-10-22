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

@Entity(tableName = "action_tiers")
data class ActionTierSetting(@PrimaryKey val actionKey: String, val tier: String)

@Entity(tableName = "shiv_settings")
data class ShivSettings(
    @PrimaryKey val id: Int = 1,
    val proactiveEnabled: Boolean = true,
    val quietStartMinute: Int = 22 * 60,
    val quietEndMinute: Int = 8 * 60,
    val maxDailyInterruptions: Int = 3
)

@Entity(tableName = "pending_events", indices = [Index("startsAt")])
data class PendingEventRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val startsAt: Long,
    val place: String? = null,
    val person: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface ShivDao {
    @Query("SELECT * FROM chat_messages ORDER BY createdAt") fun messages(): Flow<List<ChatMessage>>
    @Upsert suspend fun addMessage(value: ChatMessage)
    @Query("SELECT * FROM chat_messages ORDER BY createdAt") suspend fun allMessages(): List<ChatMessage>
    @Upsert suspend fun saveProfile(value: UserProfile)
    @Query("SELECT * FROM user_profile WHERE id = 1") fun profile(): Flow<UserProfile?>
    @Query("SELECT * FROM user_profile WHERE id = 1") suspend fun profileOnce(): UserProfile?
    @Query("SELECT * FROM memories ORDER BY createdAt DESC") fun memories(): Flow<List<MemoryFact>>
    @Query("SELECT * FROM memories ORDER BY createdAt") suspend fun allMemories(): List<MemoryFact>
    @Query("SELECT * FROM memories ORDER BY createdAt DESC LIMIT 100") suspend fun latestMemories(): List<MemoryFact>
    @Upsert suspend fun saveMemory(value: MemoryFact)
    @Query("DELETE FROM memories WHERE id = :id") suspend fun deleteMemory(id: String)
    @Query("SELECT * FROM events WHERE startsAt >= :from ORDER BY startsAt") fun events(from: Long): Flow<List<EventRecord>>
    @Query("SELECT * FROM events WHERE startsAt >= :from ORDER BY startsAt") suspend fun upcomingEvents(from: Long): List<EventRecord>
    @Query("SELECT * FROM events ORDER BY startsAt") suspend fun allEvents(): List<EventRecord>
    @Query("SELECT * FROM pending_events WHERE startsAt >= :from ORDER BY startsAt") fun pendingEvents(from: Long): Flow<List<PendingEventRecord>>
    @Query("SELECT * FROM pending_events WHERE id = :id") suspend fun pendingEvent(id: String): PendingEventRecord?
    @Query("SELECT * FROM pending_events ORDER BY startsAt") suspend fun allPendingEvents(): List<PendingEventRecord>
    @Insert suspend fun addPendingEvent(value: PendingEventRecord)
    @Query("DELETE FROM pending_events WHERE id = :id") suspend fun deletePendingEvent(id: String)
    @Query("DELETE FROM pending_events") suspend fun clearPendingEvents()
    @Query("SELECT * FROM events WHERE source = 'CALENDAR'") suspend fun calendarEvents(): List<EventRecord>
    @Upsert suspend fun saveEvent(value: EventRecord)
    @Query("DELETE FROM events WHERE id = :id") suspend fun deleteEvent(id: String)
    @Query("DELETE FROM events WHERE source = 'CALENDAR'") suspend fun clearCalendarEvents()
    @Upsert suspend fun logAction(value: ActionLogEntry)
    @Query("SELECT * FROM action_log ORDER BY createdAt") suspend fun allActions(): List<ActionLogEntry>
    @Query("SELECT * FROM reminder_rules ORDER BY ruleKey") suspend fun allRules(): List<ReminderRule>
    @Query("SELECT * FROM action_log ORDER BY createdAt DESC LIMIT 200") fun actionLog(): Flow<List<ActionLogEntry>>
    @Query("SELECT * FROM reminder_rules WHERE ruleKey = :key") suspend fun reminderRule(key: String): ReminderRule?
    @Upsert suspend fun saveRule(value: ReminderRule)
    @Query("SELECT * FROM reply_drafts ORDER BY createdAt DESC") fun drafts(): Flow<List<ReplyDraft>>
    @Query("SELECT * FROM reply_drafts ORDER BY createdAt") suspend fun allDrafts(): List<ReplyDraft>
    @Upsert suspend fun saveDraft(value: ReplyDraft)
    @Query("SELECT * FROM action_tiers ORDER BY actionKey") fun actionTiers(): Flow<List<ActionTierSetting>>
    @Query("SELECT * FROM action_tiers ORDER BY actionKey") suspend fun allActionTiers(): List<ActionTierSetting>
    @Query("SELECT tier FROM action_tiers WHERE actionKey = :key") suspend fun actionTier(key: String): String?
    @Upsert suspend fun saveActionTier(value: ActionTierSetting)
    @Query("SELECT * FROM shiv_settings WHERE id = 1") fun settings(): Flow<ShivSettings?>
    @Query("SELECT * FROM shiv_settings WHERE id = 1") suspend fun settingsOnce(): ShivSettings?
    @Upsert suspend fun saveSettings(value: ShivSettings)
    @Query("DELETE FROM shiv_settings") suspend fun clearSettings()
    @Query("SELECT COUNT(*) FROM action_log WHERE action = :action AND createdAt >= :since") suspend fun countActionsSince(action: String, since: Long): Int
    @Query("SELECT EXISTS(SELECT 1 FROM action_log WHERE action = 'Proactive notification' AND outcome = :eventId)") suspend fun wasProactiveSent(eventId: String): Boolean
    @Upsert suspend fun saveMessages(values: List<ChatMessage>)
    @Upsert suspend fun saveMemories(values: List<MemoryFact>)
    @Upsert suspend fun saveEvents(values: List<EventRecord>)
    @Upsert suspend fun savePendingEvents(values: List<PendingEventRecord>)
    @Upsert suspend fun saveActions(values: List<ActionLogEntry>)
    @Upsert suspend fun saveRules(values: List<ReminderRule>)
    @Upsert suspend fun saveDrafts(values: List<ReplyDraft>)
    @Upsert suspend fun saveActionTiers(values: List<ActionTierSetting>)
    @Query("DELETE FROM chat_messages") suspend fun clearMessages()
    @Query("DELETE FROM user_profile") suspend fun clearProfile()
    @Query("DELETE FROM memories") suspend fun clearMemories()
    @Query("DELETE FROM events") suspend fun clearEvents()
    @Query("DELETE FROM action_log") suspend fun clearLog()
    @Query("DELETE FROM reminder_rules") suspend fun clearRules()
    @Query("DELETE FROM reply_drafts") suspend fun clearDrafts()
    @Query("DELETE FROM action_tiers") suspend fun clearActionTiers()
}

@Database(
    entities = [ChatMessage::class, UserProfile::class, MemoryFact::class, EventRecord::class, ActionLogEntry::class, ReminderRule::class, ReplyDraft::class, ActionTierSetting::class, ShivSettings::class, PendingEventRecord::class],
    version = 4,
    exportSchema = true
)
abstract class ShivDatabase : RoomDatabase() {
    abstract fun dao(): ShivDao

    companion object {
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS action_tiers (actionKey TEXT NOT NULL, tier TEXT NOT NULL, PRIMARY KEY(actionKey))")
            }
        }
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS shiv_settings (id INTEGER NOT NULL, proactiveEnabled INTEGER NOT NULL, quietStartMinute INTEGER NOT NULL, quietEndMinute INTEGER NOT NULL, maxDailyInterruptions INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("INSERT OR IGNORE INTO shiv_settings (id, proactiveEnabled, quietStartMinute, quietEndMinute, maxDailyInterruptions) VALUES (1, 1, 1320, 480, 3)")
            }
        }
        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS pending_events (id TEXT NOT NULL, title TEXT NOT NULL, startsAt INTEGER NOT NULL, place TEXT, person TEXT, createdAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_events_startsAt ON pending_events(startsAt)")
            }
        }
        @Volatile private var instance: ShivDatabase? = null
        fun get(context: Context): ShivDatabase = instance ?: synchronized(this) {
            instance ?: run {
                System.loadLibrary("sqlcipher")
                val key = LocalKeyStore(context).databaseKey()
                val factory = SupportOpenHelperFactory(key)
                Room.databaseBuilder(context, ShivDatabase::class.java, "shiv-memory.db")
                    .openHelperFactory(factory)
                    .addMigrations(MIGRATION_1_2)
                    .addMigrations(MIGRATION_2_3)
                    .addMigrations(MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
        }
    }
}

class ShivRepository(context: Context) {
    val dao: ShivDao = ShivDatabase.get(context.applicationContext).dao()
}
