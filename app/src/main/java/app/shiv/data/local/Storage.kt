package app.shiv.data.local

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.util.UUID
import app.shiv.platform.LocalKeyStore

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
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "'OWNER'") val source: String = "OWNER",
    val confidence: Double? = null
)

@Entity(tableName = "events", indices = [Index("startsAt"), Index("source")])
data class DbEventRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(), val title: String,
    val startsAt: Long, val place: String? = null, val person: String? = null,
    val source: String = "CHAT", val createdAt: Long = System.currentTimeMillis(),
    val confidence: Double? = null
)

@Entity(tableName = "action_log", indices = [Index("createdAt")])
data class DbActionLogEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val action: String,
    val tier: String,
    val outcome: String,
    val createdAt: Long = System.currentTimeMillis(),
    val actionType: String? = null,
    @ColumnInfo(defaultValue = "'{}'") val argumentsJson: String = "{}",
    val permissionState: String? = null,
    val confirmationState: String? = null,
    val result: String? = null,
    val undoInfo: String? = null
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

@Entity(tableName = "tasks", indices = [Index("status"), Index("dueAt"), Index("source")])
data class DbTaskRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val notes: String = "",
    val dueAt: Long? = null,
    val status: String = "OPEN",
    val source: String = "OWNER",
    val confidence: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "reminders",
    foreignKeys = [ForeignKey(entity = DbEventRecord::class, parentColumns = ["id"], childColumns = ["eventId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["eventId", "triggerAt"], unique = true), Index("deliveryState"), Index("triggerAt")]
)
data class DbReminderRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val eventId: String,
    val triggerAt: Long,
    val deliveryState: String = "PENDING",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "model_metadata")
data class DbModelMetadata(
    @PrimaryKey val role: String,
    val format: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val importedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "record_sources", indices = [Index(value = ["recordType", "recordId", "source"], unique = true), Index("capturedAt")])
data class DbRecordSourceMetadata(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val recordType: String,
    val recordId: String,
    val source: String,
    val confidence: Double? = null,
    val capturedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "capture_queue", indices = [Index(value = ["status", "nextAttemptAt", "priority", "capturedAt"]), Index("leaseUntil")])
data class DbCaptureSignal(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val payload: String,
    val source: String,
    val capturedAt: Long = System.currentTimeMillis(),
    val priority: Int = 1,
    val status: String = "QUEUED",
    val attempts: Int = 0,
    val nextAttemptAt: Long = capturedAt,
    val leaseToken: String? = null,
    val leaseUntil: Long? = null,
    val processedAt: Long? = null,
    val failureCode: String? = null
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
    @Upsert suspend fun saveTask(value: DbTaskRecord)
    @Query("SELECT * FROM tasks ORDER BY CASE WHEN dueAt IS NULL THEN 1 ELSE 0 END, dueAt, createdAt") fun tasks(): Flow<List<DbTaskRecord>>
    @Query("SELECT * FROM tasks ORDER BY createdAt") suspend fun allTasks(): List<DbTaskRecord>
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun task(id: String): DbTaskRecord?
    @Query("DELETE FROM tasks WHERE id = :id") suspend fun deleteTask(id: String)
    @Query("DELETE FROM tasks") suspend fun clearTasks()
    @Upsert suspend fun saveReminder(value: DbReminderRecord)
    @Query("SELECT * FROM reminders WHERE id = :id") suspend fun reminder(id: String): DbReminderRecord?
    @Query("SELECT * FROM reminders WHERE eventId = :eventId ORDER BY triggerAt") suspend fun remindersForEvent(eventId: String): List<DbReminderRecord>
    @Query("SELECT * FROM reminders WHERE deliveryState = 'PENDING' ORDER BY triggerAt LIMIT :limit") suspend fun pendingReminders(limit: Int): List<DbReminderRecord>
    @Query("UPDATE reminders SET deliveryState = :state, updatedAt = :updatedAt WHERE id = :id") suspend fun setReminderState(id: String, state: String, updatedAt: Long): Int
    @Query("DELETE FROM reminders WHERE id = :id") suspend fun deleteReminder(id: String)
    @Query("DELETE FROM reminders") suspend fun clearReminders()
    @Upsert suspend fun saveModelMetadata(value: DbModelMetadata)
    @Query("SELECT * FROM model_metadata ORDER BY role") fun modelMetadata(): Flow<List<DbModelMetadata>>
    @Query("SELECT * FROM model_metadata WHERE role = :role") suspend fun modelMetadata(role: String): DbModelMetadata?
    @Query("DELETE FROM model_metadata WHERE role = :role") suspend fun deleteModelMetadata(role: String)
    @Query("DELETE FROM model_metadata") suspend fun clearModelMetadata()
    @Upsert suspend fun saveRecordSource(value: DbRecordSourceMetadata)
    @Query("SELECT * FROM record_sources WHERE recordType = :recordType AND recordId = :recordId ORDER BY capturedAt") suspend fun recordSources(recordType: String, recordId: String): List<DbRecordSourceMetadata>
    @Query("DELETE FROM record_sources WHERE recordType = :recordType AND recordId = :recordId") suspend fun deleteRecordSources(recordType: String, recordId: String)
    @Query("DELETE FROM record_sources") suspend fun clearRecordSources()
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun enqueueCapture(value: DbCaptureSignal): Long
    @Query("SELECT * FROM capture_queue WHERE id = :id") suspend fun capture(id: String): DbCaptureSignal?
    @Query("SELECT * FROM capture_queue ORDER BY capturedAt, id") suspend fun allCaptures(): List<DbCaptureSignal>
    @Query("SELECT * FROM capture_queue WHERE nextAttemptAt <= :now AND (status IN ('QUEUED','RETRY') OR (status = 'PROCESSING' AND leaseUntil <= :now)) ORDER BY priority DESC, capturedAt, id LIMIT 1") suspend fun nextCapture(now: Long): DbCaptureSignal?
    @Query("UPDATE capture_queue SET status = 'PROCESSING', attempts = attempts + 1, leaseToken = :token, leaseUntil = :leaseUntil, failureCode = NULL WHERE id = :id AND nextAttemptAt <= :now AND (status IN ('QUEUED','RETRY') OR (status = 'PROCESSING' AND leaseUntil <= :now))") suspend fun claimCapture(id: String, token: String, now: Long, leaseUntil: Long): Int
    @Query("UPDATE capture_queue SET payload = '', status = 'COMPLETED', processedAt = :processedAt, leaseToken = NULL, leaseUntil = NULL, failureCode = NULL WHERE id = :id AND status = 'PROCESSING' AND leaseToken = :token") suspend fun completeCapture(id: String, token: String, processedAt: Long): Int
    @Query("UPDATE capture_queue SET status = 'RETRY', nextAttemptAt = :nextAttemptAt, leaseToken = NULL, leaseUntil = NULL, failureCode = :failureCode WHERE id = :id AND status = 'PROCESSING' AND leaseToken = :token") suspend fun retryCapture(id: String, token: String, nextAttemptAt: Long, failureCode: String): Int
    @Query("UPDATE capture_queue SET status = 'FAILED', leaseToken = NULL, leaseUntil = NULL, failureCode = :failureCode WHERE id = :id AND status = 'PROCESSING' AND leaseToken = :token") suspend fun failCapture(id: String, token: String, failureCode: String): Int
    @Query("DELETE FROM capture_queue WHERE id = :id") suspend fun deleteCapture(id: String)
    @Query("DELETE FROM capture_queue") suspend fun clearCaptures()
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
    entities = [DbChatMessage::class, DbUserProfile::class, DbMemoryFact::class, DbEventRecord::class, DbActionLogEntry::class, DbReminderRule::class, DbReplyDraft::class, DbActionTierSetting::class, DbShivSettings::class, DbPendingEventRecord::class, DbTaskRecord::class, DbReminderRecord::class, DbModelMetadata::class, DbRecordSourceMetadata::class, DbCaptureSignal::class],
    version = 6,
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
        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE action_log ADD COLUMN actionType TEXT")
                db.execSQL("ALTER TABLE action_log ADD COLUMN argumentsJson TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("ALTER TABLE action_log ADD COLUMN permissionState TEXT")
                db.execSQL("ALTER TABLE action_log ADD COLUMN confirmationState TEXT")
                db.execSQL("ALTER TABLE action_log ADD COLUMN result TEXT")
                db.execSQL("ALTER TABLE action_log ADD COLUMN undoInfo TEXT")
            }
        }
        internal val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memories ADD COLUMN source TEXT NOT NULL DEFAULT 'OWNER'")
                db.execSQL("ALTER TABLE memories ADD COLUMN confidence REAL")
                db.execSQL("ALTER TABLE events ADD COLUMN confidence REAL")
                db.execSQL("CREATE TABLE IF NOT EXISTS tasks (id TEXT NOT NULL, title TEXT NOT NULL, notes TEXT NOT NULL, dueAt INTEGER, status TEXT NOT NULL, source TEXT NOT NULL, confidence REAL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_status ON tasks(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_dueAt ON tasks(dueAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_source ON tasks(source)")
                db.execSQL("CREATE TABLE IF NOT EXISTS reminders (id TEXT NOT NULL, eventId TEXT NOT NULL, triggerAt INTEGER NOT NULL, deliveryState TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(eventId) REFERENCES events(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_reminders_eventId_triggerAt ON reminders(eventId, triggerAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_deliveryState ON reminders(deliveryState)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_triggerAt ON reminders(triggerAt)")
                db.execSQL("CREATE TABLE IF NOT EXISTS model_metadata (role TEXT NOT NULL, format TEXT NOT NULL, fileName TEXT NOT NULL, sizeBytes INTEGER NOT NULL, sha256 TEXT NOT NULL, importedAt INTEGER NOT NULL, PRIMARY KEY(role))")
                db.execSQL("CREATE TABLE IF NOT EXISTS record_sources (id TEXT NOT NULL, recordType TEXT NOT NULL, recordId TEXT NOT NULL, source TEXT NOT NULL, confidence REAL, capturedAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_record_sources_recordType_recordId_source ON record_sources(recordType, recordId, source)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_record_sources_capturedAt ON record_sources(capturedAt)")
                db.execSQL("CREATE TABLE IF NOT EXISTS capture_queue (id TEXT NOT NULL, payload TEXT NOT NULL, source TEXT NOT NULL, capturedAt INTEGER NOT NULL, priority INTEGER NOT NULL, status TEXT NOT NULL, attempts INTEGER NOT NULL, nextAttemptAt INTEGER NOT NULL, leaseToken TEXT, leaseUntil INTEGER, processedAt INTEGER, failureCode TEXT, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_capture_queue_status_nextAttemptAt_priority_capturedAt ON capture_queue(status, nextAttemptAt, priority, capturedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_capture_queue_leaseUntil ON capture_queue(leaseUntil)")
            }
        }
        @Volatile private var instance: ShivDatabase? = null
        fun get(context: Context): ShivDatabase = instance ?: synchronized(this) {
            instance ?: run {
                openProduction(context.applicationContext, "shiv-memory.db")
                    .also { instance = it }
            }
        }

        /** Production key retrieval plus SQLCipher open; the custom name is used by isolated instrumentation tests. */
        internal fun openProduction(context: Context, name: String): ShivDatabase {
            val key = LocalKeyStore(context, name).databaseKey()
            return openEncrypted(context.applicationContext, name, key)
        }

        /** Same SQLCipher path as production get(), exposed internally for encrypted persistence tests. */
        internal fun openEncrypted(context: Context, name: String, key: ByteArray): ShivDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context.applicationContext, ShivDatabase::class.java, name)
                .openHelperFactory(SupportOpenHelperFactory(key))
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
        }

        internal fun closeSingletonForTests() = synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}
