package app.shiv

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.util.UUID

@Entity(tableName = "chat_messages")
data class DbChatMessage(
    @PrimaryKey val id: String = UUID.randomUUID().toString(), val text: String,
    val fromUser: Boolean, val createdAt: Long = System.currentTimeMillis(), val tier: String? = null
)

@Entity(tableName = "user_profile")
data class DbUserProfile(
    @PrimaryKey val id: Int = 1, val name: String = "", val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "memories", indices = [Index("createdAt")])
data class DbMemoryFact(
    @PrimaryKey val id: String = UUID.randomUUID().toString(), val text: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "events", indices = [Index("startsAt"), Index("source")])
data class DbEventRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(), val title: String,
    val startsAt: Long, val place: String? = null, val person: String? = null,
    val source: String = "CHAT", val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "action_log", indices = [Index("createdAt")])
data class DbActionLogEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(), val action: String,
    val tier: String, val outcome: String, val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reminder_rules")
data class DbReminderRule(@PrimaryKey val ruleKey: String, val value: String, val updatedAt: Long = System.currentTimeMillis())

@Entity(tableName = "reply_drafts", indices = [Index("createdAt")])
data class DbReplyDraft(
    @PrimaryKey val id: String = UUID.randomUUID().toString(), val person: String,
    val body: String, val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "action_tiers")
data class DbActionTierSetting(@PrimaryKey val actionKey: String, val tier: String)

@Entity(tableName = "shiv_settings")
data class DbShivSettings(
    @PrimaryKey val id: Int = 1, val proactiveEnabled: Boolean = true,
    val quietStartMinute: Int = 22 * 60, val quietEndMinute: Int = 8 * 60,
    val maxDailyInterruptions: Int = 3
)

@Entity(tableName = "pending_events", indices = [Index("startsAt")])
data class DbPendingEventRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(), val title: String,
    val startsAt: Long, val place: String? = null, val person: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
@Dao
interface ShivDao {
    @Query("SELECT * FROM chat_messages ORDER BY createdAt") fun messages(): Flow<List<DbChatMessage>>
    @Upsert suspend fun addMessage(value: DbChatMessage)
    @Query("SELECT * FROM chat_messages ORDER BY createdAt") suspend fun allMessages(): List<DbChatMessage>
    @Upsert suspend fun saveProfile(value: DbUserProfile)
    @Query("SELECT * FROM user_profile WHERE id = 1") fun profile(): Flow<DbUserProfile?>
    @Query("SELECT * FROM user_profile WHERE id = 1") suspend fun profileOnce(): DbUserProfile?
    @Query("SELECT * FROM memories ORDER BY createdAt DESC") fun memories(): Flow<List<DbMemoryFact>>
    @Query("SELECT * FROM memories ORDER BY createdAt") suspend fun allMemories(): List<DbMemoryFact>
    @Query("SELECT * FROM memories ORDER BY createdAt DESC LIMIT 100") suspend fun latestMemories(): List<DbMemoryFact>
    @Upsert suspend fun saveMemory(value: DbMemoryFact)
    @Query("DELETE FROM memories WHERE id = :id") suspend fun deleteMemory(id: String)
    @Query("SELECT * FROM events WHERE startsAt >= :from ORDER BY startsAt") fun events(from: Long): Flow<List<DbEventRecord>>
    @Query("SELECT * FROM events WHERE startsAt >= :from ORDER BY startsAt") suspend fun upcomingEvents(from: Long): List<DbEventRecord>
    @Query("SELECT * FROM events WHERE id = :id") suspend fun eventById(id: String): DbEventRecord?
    @Query("SELECT * FROM events WHERE startsAt > :from AND startsAt <= :until AND NOT EXISTS (SELECT 1 FROM action_log WHERE action IN ('Proactive notification', 'Proactive notification reserved') AND outcome = events.id) ORDER BY startsAt, id LIMIT 8")
    suspend fun proactiveEvents(from: Long, until: Long): List<DbEventRecord>
    @Query("SELECT * FROM events ORDER BY startsAt") suspend fun allEvents(): List<DbEventRecord>
    @Query("SELECT * FROM pending_events WHERE startsAt >= :from ORDER BY startsAt") fun pendingEvents(from: Long): Flow<List<DbPendingEventRecord>>
    @Query("SELECT * FROM pending_events WHERE id = :id") suspend fun pendingEvent(id: String): DbPendingEventRecord?
    @Query("SELECT * FROM pending_events ORDER BY startsAt") suspend fun allPendingEvents(): List<DbPendingEventRecord>
    @Insert suspend fun addPendingEvent(value: DbPendingEventRecord)
    @Query("DELETE FROM pending_events WHERE id = :id") suspend fun deletePendingEvent(id: String)
    @Query("DELETE FROM pending_events") suspend fun clearPendingEvents()
    @Query("SELECT * FROM events WHERE source = 'CALENDAR'") suspend fun calendarEvents(): List<DbEventRecord>
    @Upsert suspend fun saveEvent(value: DbEventRecord)
    @Query("DELETE FROM events WHERE id = :id") suspend fun deleteEvent(id: String)
    @Query("DELETE FROM events WHERE source = 'CALENDAR'") suspend fun clearCalendarEvents()
    @Query("SELECT * FROM action_log WHERE id = :id") suspend fun actionById(id: String): DbActionLogEntry?
    @Query("DELETE FROM action_log WHERE id = :id") suspend fun deleteAction(id: String)
    @Query("SELECT COUNT(*) FROM action_log WHERE action IN ('Proactive notification', 'Proactive notification reserved') AND createdAt >= :since") suspend fun countProactiveSince(since: Long): Int
    @Upsert suspend fun logAction(value: DbActionLogEntry)
    @Query("SELECT * FROM action_log ORDER BY createdAt") suspend fun allActions(): List<DbActionLogEntry>
    @Query("SELECT * FROM reminder_rules ORDER BY ruleKey") suspend fun allRules(): List<DbReminderRule>
    @Query("SELECT * FROM action_log ORDER BY createdAt DESC LIMIT 200") fun actionLog(): Flow<List<DbActionLogEntry>>
    @Query("SELECT * FROM reminder_rules WHERE ruleKey = :key") suspend fun reminderRule(key: String): DbReminderRule?
    @Upsert suspend fun saveRule(value: DbReminderRule)
    @Query("SELECT * FROM reply_drafts ORDER BY createdAt DESC") fun drafts(): Flow<List<DbReplyDraft>>
    @Query("SELECT * FROM reply_drafts ORDER BY createdAt") suspend fun allDrafts(): List<DbReplyDraft>
    @Upsert suspend fun saveDraft(value: DbReplyDraft)
    @Query("SELECT * FROM action_tiers ORDER BY actionKey") fun actionTiers(): Flow<List<DbActionTierSetting>>
    @Query("SELECT * FROM action_tiers ORDER BY actionKey") suspend fun allActionTiers(): List<DbActionTierSetting>
    @Query("SELECT tier FROM action_tiers WHERE actionKey = :key") suspend fun actionTier(key: String): String?
    @Upsert suspend fun saveActionTier(value: DbActionTierSetting)
    @Query("SELECT * FROM shiv_settings WHERE id = 1") fun settings(): Flow<DbShivSettings?>
    @Query("SELECT * FROM shiv_settings WHERE id = 1") suspend fun settingsOnce(): DbShivSettings?
    @Upsert suspend fun saveSettings(value: DbShivSettings)
    @Query("DELETE FROM shiv_settings") suspend fun clearSettings()
    @Query("SELECT COUNT(*) FROM action_log WHERE action = :action AND createdAt >= :since") suspend fun countActionsSince(action: String, since: Long): Int
    @Query("SELECT EXISTS(SELECT 1 FROM action_log WHERE action IN ('Proactive notification', 'Proactive notification reserved') AND outcome = :eventId)") suspend fun wasProactiveSent(eventId: String): Boolean
    @Upsert suspend fun saveMessages(values: List<DbChatMessage>)
    @Upsert suspend fun saveMemories(values: List<DbMemoryFact>)
    @Upsert suspend fun saveEvents(values: List<DbEventRecord>)
    @Upsert suspend fun savePendingEvents(values: List<DbPendingEventRecord>)
    @Upsert suspend fun saveActions(values: List<DbActionLogEntry>)
    @Upsert suspend fun saveRules(values: List<DbReminderRule>)
    @Upsert suspend fun saveDrafts(values: List<DbReplyDraft>)
    @Upsert suspend fun saveActionTiers(values: List<DbActionTierSetting>)
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
    entities = [DbChatMessage::class, DbUserProfile::class, DbMemoryFact::class, DbEventRecord::class, DbActionLogEntry::class, DbReminderRule::class, DbReplyDraft::class, DbActionTierSetting::class, DbShivSettings::class, DbPendingEventRecord::class],
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
