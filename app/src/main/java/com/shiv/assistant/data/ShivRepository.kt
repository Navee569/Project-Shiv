package com.shiv.assistant.data

import android.content.Context
import com.shiv.assistant.domain.SensitiveAppDenylist
import com.shiv.assistant.domain.SourceExclusions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import org.json.JSONArray
import androidx.room.withTransaction

class ShivRepository(private val context: Context) {
    private val database = EncryptedDatabase.open(context)
    private val dao = database.dao()
    suspend fun incoming(source: String, fact: String, priority: Int = 1, packageName: String? = null): String? =
        withContext(Dispatchers.IO) {
            // Critical: never persist or pass denylisted content to a model, including an audit log.
            if (packageName != null && SourceExclusions.shouldBlock(context,packageName)) return@withContext null
            require(priority in 0..10)
            val compact = fact.trim().take(1500)
            if (compact.isBlank()) return@withContext null
            val id = UUID.randomUUID().toString()
            dao.enqueue(SignalRecord(id,source,compact,priority))
            id
        }
    suspend fun contextSnapshot(now: Long = System.currentTimeMillis()): ContextSnapshot =
        ContextSnapshot(Instant.ofEpochMilli(now).toString(),
            dao.events(now,now+48*60*60*1000L),dao.recentMemories(now,12),
            dao.rules(),dao.recentChats(8),dao.actions(10))
    suspend fun remember(text: String,category: String = "FACT",source: String = "owner"): MemoryRecord {
        require(text.isNotBlank())
        val entry = MemoryRecord(UUID.randomUUID().toString(),text.trim().take(8192),category,source,
            confidence=if(source=="owner") 1.0 else 0.5,explicit=source=="owner")
        dao.putMemory(entry)
        return entry
    }
    /**
     * Corrections and deletion update the canonical fact AND the legacy
     * encrypted vault note index in one SQLCipher transaction. Old facts
     * cannot reappear in local chat context after an owner correction.
     */
    suspend fun correct(oldId: String,replacement: String): MemoryRecord =
        database.withTransaction {
            require(replacement.isNotBlank())
            val old=dao.memory(oldId)?:error("Memory no longer exists")
            val explicit=old.copy(id=UUID.randomUUID().toString(),
                text=replacement.trim().take(8192),source="owner-correction",
                explicit=true,confidence=1.0,supersedes=old.id,
                updatedAt=System.currentTimeMillis())
            dao.putMemory(explicit)
            dao.removeMemory(oldId)
            modifyLegacyNote(old.text,explicit.text)
            explicit
        }
    private suspend fun modifyLegacyNote(previous:String,replacement:String?) {
        val current=dao.vault("notes")?:return
        val values=JSONArray(current.value)
        val revised=JSONArray()
        for(i in 0 until values.length()) {
            val item=values.getString(i)
            if(item.trim()==previous.trim()) {
                if(replacement!=null)revised.put(replacement)
            } else revised.put(item)
        }
        dao.putVault(VaultEntry("notes",revised.toString()))
    }
    suspend fun search(query: String): List<MemoryRecord> {
        val words=query.lowercase().split(Regex("\\W+")).filter{it.length>=3}.take(6)
        return dao.recentMemories(System.currentTimeMillis(),100)
            .map { item -> item to (words.count{item.text.lowercase().contains(it)}*4.0 +
                item.confidence*2.0 + if(item.explicit) 3.0 else 0.0) }
            .filter{words.isEmpty()||it.second>=4}
            .sortedByDescending{it.second}.take(12).map{it.first}
    }
    suspend fun purgeExpired(now:Long=System.currentTimeMillis()):Int =
        database.withTransaction { dao.purgeExpiredMemories(now) }
    suspend fun forget(id:String) = database.withTransaction {
        val record=dao.memory(id)
        if(record!=null) {
            dao.removeMemory(id)
            modifyLegacyNote(record.text,null)
        }
    }
    suspend fun upcoming(start:Long,end:Long)=dao.events(start,end)
    suspend fun beliefs()=dao.beliefs()
    suspend fun forgetBelief(id:String)=dao.deleteBelief(id)
}
data class ContextSnapshot(val asOf:String,val events:List<EventRecord>,
    val relevantMemories:List<MemoryRecord>,val rules:List<RuleRecord>,
    val recentConversations:List<ChatRecord>,val recentActions:List<ActionLogRecord>) {
    fun boundedSummary()=buildString {
        append("Date/time: ").append(asOf)
        append("\nEvents: ").append(events.take(8).joinToString("; "){it.title+" @ "+it.startAt})
        append("\nMemories: ").append(relevantMemories.take(8).joinToString("; "){it.text.take(100)})
        append("\nRules: ").append(rules.take(8).joinToString("; "){it.description.take(80)})
    }.take(4000)
}
