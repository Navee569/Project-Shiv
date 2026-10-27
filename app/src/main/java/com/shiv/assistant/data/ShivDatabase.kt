package com.shiv.assistant.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "vault_entries")
data class VaultEntry(@PrimaryKey val name: String, val value: String)

@Entity(tableName = "profiles")
data class ProfileRecord(@PrimaryKey val id: String = "owner", val displayName: String = "", val language: String = "en-IN", val updatedAt: Long = System.currentTimeMillis())

@Entity(tableName = "memories", indices = [Index("category"), Index("updatedAt")])
data class MemoryRecord(
    @PrimaryKey val id: String, val text: String, val category: String, val source: String,
    val confidence: Double = 1.0, val explicit: Boolean = true, val supersedes: String? = null,
    val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt,
    val expiresAt: Long? = null
)
@Entity(tableName = "events", indices = [Index("startAt")])
data class EventRecord(@PrimaryKey val id: String, val title: String, val startAt: Long, val endAt: Long?,
    val source: String, val confidence: Double = 1.0, val createdAt: Long = System.currentTimeMillis())
@Entity(tableName = "tasks", indices = [Index("dueAt"), Index("status")])
data class TaskRecord(@PrimaryKey val id: String, val title: String, val status: String = "OPEN",
    val dueAt: Long? = null, val eventId: String? = null, val createdAt: Long = System.currentTimeMillis())
@Entity(tableName = "reminders", indices = [Index("dueAt")])
data class ReminderRecord(@PrimaryKey val id: String, val label: String, val dueAt: Long, val eventId: String?,
    val status: String = "SCHEDULED")
@Entity(tableName = "saved_rules")
data class RuleRecord(@PrimaryKey val id: String, val description: String, val key: String,
    val value: String, val enabled: Boolean = true, val createdAt: Long = System.currentTimeMillis())
@Entity(tableName = "chat_history", indices = [Index("createdAt")])
data class ChatRecord(@PrimaryKey val id: String, val role: String, val body: String,
    val createdAt: Long = System.currentTimeMillis())
@Entity(tableName = "action_log", indices = [Index("createdAt")])
data class ActionLogRecord(@PrimaryKey val id: String, val type: String, val argsRedacted: String, val tier: String,
    val permission: String, val confirmed: Boolean, val result: String, val undo: String?,
    val createdAt: Long = System.currentTimeMillis())
@Entity(tableName = "permission_tiers")
data class PermissionTierRecord(@PrimaryKey val actionType: String, val tier: String, val changedAt: Long)
@Entity(tableName = "models")
data class ModelRecord(@PrimaryKey val id: String, val name: String, val role: String,
    val uri: String, val sha256: String, val bytes: Long, val format: String,
    val importedAt: Long = System.currentTimeMillis(), val status: String = "IMPORTED")
@Entity(tableName = "signals", indices = [Index(value=["status","priority","receivedAt"])])
data class SignalRecord(
    @PrimaryKey val id: String, val source: String, val fact: String, val priority: Int,
    val status: String = "PENDING", val receivedAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0, val lastError: String? = null, val processedAt: Long? = null
)
@Entity(tableName = "beliefs", indices=[Index("updatedAt")])
data class BeliefRecord(@PrimaryKey val id: String, val hypothesis: String, val confidence: Double,
    val evidence: String, val source: String, val lastConfirmedAt: Long?, val updatedAt: Long,
    val correctedByOwner: Boolean = false)
@Entity(tableName = "decision_traces", indices=[Index("createdAt")])
data class DecisionTraceRecord(@PrimaryKey val id: String, val signalId: String?, val decision: String,
    val reason: String, val policyTier: String, val createdAt: Long = System.currentTimeMillis())

@Dao
interface ShivDao {
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putVault(value: VaultEntry)
    @Query("SELECT * FROM vault_entries WHERE name=:name") suspend fun vault(name:String): VaultEntry?

    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putProfile(value: ProfileRecord)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putMemory(value: MemoryRecord)
    @Query("SELECT * FROM events WHERE id=:id") suspend fun event(id:String):EventRecord?
    @Query("SELECT * FROM memories WHERE text=:text AND category=:category AND source='owner' AND explicit=1 LIMIT 1")
    suspend fun matchingOwnerMemory(text:String,category:String):MemoryRecord?
    @Query("SELECT * FROM memories WHERE (expiresAt IS NULL OR expiresAt > :now) ORDER BY explicit DESC, updatedAt DESC LIMIT :limit")
    suspend fun recentMemories(now: Long, limit: Int = 50): List<MemoryRecord>
    @Query("SELECT * FROM memories WHERE id=:id") suspend fun memory(id: String): MemoryRecord?
    @Query("DELETE FROM memories WHERE id=:id") suspend fun removeMemory(id: String)
    @Query("DELETE FROM memories WHERE expiresAt IS NOT NULL AND expiresAt <= :now")
    suspend fun purgeExpiredMemories(now: Long): Int
    @Query("DELETE FROM signals WHERE status IN ('PROCESSED','FAILED') AND processedAt IS NOT NULL AND processedAt < :before")
    suspend fun purgeOldProcessedSignals(before: Long): Int
    @Query("DELETE FROM decision_traces WHERE signalId IS NOT NULL AND signalId NOT IN (SELECT id FROM signals)")
    suspend fun purgeUnreferencedTraces(): Int
    @Query("DELETE FROM chat_history") suspend fun eraseAllChats()
    @Query("DELETE FROM memories") suspend fun eraseAllMemories()
    @Query("DELETE FROM events") suspend fun eraseAllEvents()
    @Query("DELETE FROM tasks") suspend fun eraseAllTasks()
    @Query("DELETE FROM reminders") suspend fun eraseAllReminders()
    @Query("DELETE FROM saved_rules") suspend fun eraseAllRules()
    @Query("DELETE FROM beliefs") suspend fun eraseAllBeliefs()
    @Query("DELETE FROM signals") suspend fun eraseAllSignals()
    @Query("DELETE FROM decision_traces") suspend fun eraseAllTraces()
    @Query("DELETE FROM action_log") suspend fun eraseAllActions()
    @Query("DELETE FROM vault_entries") suspend fun eraseAllVault()
    @Query("DELETE FROM profiles") suspend fun eraseAllProfiles()
    @Query("DELETE FROM permission_tiers") suspend fun eraseAllTiers()
    @Transaction suspend fun eraseOwnerRecords() {
        eraseAllChats()
        eraseAllMemories()
        eraseAllEvents()
        eraseAllTasks()
        eraseAllReminders()
        eraseAllRules()
        eraseAllBeliefs()
        eraseAllSignals()
        eraseAllTraces()
        eraseAllActions()
        eraseAllVault()
        eraseAllProfiles()
        eraseAllTiers()
        // Models are intentionally preserved. Model files contain public weights,
        // not personal records; retaining them avoids stranding GB of staged files.
    }
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putEvent(value: EventRecord)
    @Query("DELETE FROM events WHERE id=:id") suspend fun deleteEvent(id:String)
    @Query("SELECT * FROM events WHERE startAt BETWEEN :start AND :end ORDER BY startAt")
    suspend fun events(start: Long, end: Long): List<EventRecord>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putTask(value: TaskRecord)
    @Query("SELECT * FROM tasks WHERE status='OPEN' ORDER BY dueAt") suspend fun pendingTasks(): List<TaskRecord>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putReminder(value: ReminderRecord)
    @Query("SELECT * FROM reminders WHERE id=:id") suspend fun reminder(id: String): ReminderRecord?
    @Query("SELECT id FROM reminders") suspend fun reminderIds(): List<String>
    @Query("UPDATE reminders SET status=:status WHERE id=:id") suspend fun reminderStatus(id:String,status:String)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putRule(value: RuleRecord)
    @Query("SELECT * FROM saved_rules WHERE enabled=1") suspend fun rules(): List<RuleRecord>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putChat(value: ChatRecord)
    @Query("SELECT * FROM chat_history ORDER BY createdAt DESC LIMIT :limit") suspend fun recentChats(limit: Int): List<ChatRecord>
    @Query("DELETE FROM chat_history") suspend fun clearChats()
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun logAction(value: ActionLogRecord)
    @Query("SELECT * FROM action_log ORDER BY createdAt DESC LIMIT :limit") suspend fun actions(limit: Int): List<ActionLogRecord>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putTier(value: PermissionTierRecord)
    @Query("SELECT * FROM permission_tiers WHERE actionType=:type") suspend fun tier(type: String): PermissionTierRecord?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putModel(value: ModelRecord)
    @Query("SELECT * FROM models ORDER BY importedAt DESC") suspend fun models(): List<ModelRecord>
    @Query("DELETE FROM models WHERE id=:id") suspend fun deleteModel(id: String)
    @Query("SELECT id FROM signals WHERE source=:source AND fact=:fact AND receivedAt >= :since AND status != 'FAILED' ORDER BY receivedAt DESC LIMIT 1")
    suspend fun recentMatchingSignal(source:String,fact:String,since:Long):String?
    @Transaction suspend fun enqueueIfNew(value:SignalRecord,since:Long):Boolean {
        if(recentMatchingSignal(value.source,value.fact,since)!=null)return false
        enqueue(value)
        return true
    }
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun enqueue(value: SignalRecord)
    @Query("SELECT * FROM signals WHERE status IN ('PENDING','RETRY') AND attempts < 8 ORDER BY priority DESC, receivedAt ASC LIMIT 1")
    suspend fun nextSignal(): SignalRecord?
    @Query("UPDATE signals SET status='PROCESSING', attempts=attempts+1 WHERE id=:id AND status IN ('PENDING','RETRY')")
    suspend fun claim(id: String): Int
    @Query("UPDATE signals SET status='PROCESSED', processedAt=:now, lastError=NULL WHERE id=:id")
    suspend fun markProcessed(id: String, now: Long)
    @Query("UPDATE signals SET status=CASE WHEN attempts >= 8 THEN 'FAILED' ELSE 'RETRY' END, lastError=:error WHERE id=:id")
    suspend fun markRetry(id: String, error: String)
    @Query("SELECT * FROM signals WHERE id=:id") suspend fun signal(id:String): SignalRecord?
    @Query("DELETE FROM signals WHERE source='owner_input'") suspend fun clearOwnerChatSignals()
    @Query("DELETE FROM decision_traces WHERE signalId IS NOT NULL AND signalId NOT IN (SELECT id FROM signals)")
    suspend fun clearOrphanDecisions()
    @Query("UPDATE signals SET status='RETRY' WHERE status='PROCESSING'")
    suspend fun recoverInFlight()
    @Query("SELECT COUNT(*) FROM signals WHERE status IN ('PENDING','RETRY') AND attempts < 8") suspend fun pendingCount(): Int
    @Query("SELECT COUNT(*) FROM signals WHERE status='FAILED'") suspend fun failedSignalCount():Int
    @Query("UPDATE signals SET status='RETRY', attempts=0, lastError=NULL WHERE status='FAILED'")
    suspend fun retryFailedSignals():Int
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putBelief(value: BeliefRecord)
    @Query("SELECT * FROM beliefs ORDER BY updatedAt DESC") suspend fun beliefs(): List<BeliefRecord>
    @Query("DELETE FROM beliefs WHERE id=:id") suspend fun deleteBelief(id: String)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun trace(value: DecisionTraceRecord)
    @Query("SELECT * FROM decision_traces WHERE id=:id") suspend fun decision(id:String):DecisionTraceRecord?
    @Query("SELECT * FROM decision_traces ORDER BY createdAt DESC LIMIT :limit") suspend fun traces(limit: Int): List<DecisionTraceRecord>
    @Query("UPDATE decision_traces SET decision='UNDONE', reason='Owner reversed approved event' WHERE id=:id AND decision='APPROVED'")
    suspend fun markApprovedUndone(id:String):Int
    @Transaction suspend fun undoApprovedEvent(decisionId:String, eventId:String,
        audit:ActionLogRecord):Boolean {
        if(markApprovedUndone(decisionId)!=1)return false
        deleteEvent(eventId)
        reminderStatus("event-"+eventId,"CANCELLED")
        logAction(audit)
        return true
    }
    @Transaction suspend fun confirmEvent(event:EventRecord, reminder:ReminderRecord,
        updated:DecisionTraceRecord, audit:ActionLogRecord) {
        putEvent(event)
        putReminder(reminder)
        trace(updated)
        logAction(audit)
    }
}

@Database(
    entities = [VaultEntry::class, ProfileRecord::class, MemoryRecord::class, EventRecord::class, TaskRecord::class,
        ReminderRecord::class, RuleRecord::class, ChatRecord::class, ActionLogRecord::class,
        PermissionTierRecord::class, ModelRecord::class, SignalRecord::class, BeliefRecord::class,
        DecisionTraceRecord::class],
    version = 1,
    exportSchema = true
)
abstract class ShivDatabase : RoomDatabase() {
    abstract fun dao(): ShivDao
}
