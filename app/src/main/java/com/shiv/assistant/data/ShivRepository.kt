package com.shiv.assistant.data

import android.content.Context
import com.shiv.assistant.domain.SensitiveAppDenylist
import com.shiv.assistant.domain.SourceExclusions
import com.shiv.assistant.domain.AppSourceAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import org.json.JSONArray
import androidx.room.withTransaction
import com.shiv.assistant.automation.SignalWorker

class ShivRepository(private val context: Context) {
    private val database = EncryptedDatabase.open(context)
    private val dao = database.dao()
    suspend fun incoming(source: String, fact: String, priority: Int = 1, packageName: String? = null): String? =
        withContext(Dispatchers.IO) {
            // Critical: never persist or pass denylisted content to a model, including an audit log.
            if (packageName != null && !AppSourceAccess.mayRead(context,packageName,source))
                return@withContext null
            require(priority in 0..10)
            val compact = fact.trim().take(1500)
            if (compact.isBlank()) return@withContext null
            val id = UUID.randomUUID().toString()
            val normalized = packageName?.trim()?.lowercase(java.util.Locale.ROOT)
            val safeSource = if(normalized!=null) source+":"+normalized else source
            val saved=SignalRecord(id,safeSource,compact,priority)
            // User-typed commands are intentional and should not be deduplicated.
            // Only explicitly safe, low-priority background facts may merge.
            val window=when {
                normalized!=null -> 10 * 60_000L
                else -> 0L
            }
            val result=if(window==0L) {
                dao.enqueue(saved)
                id
            } else {
                if(dao.enqueueIfNew(saved,saved.receivedAt-window))id else null
            }
            // Recovery on app start and boot covers a crash between these calls.
            if(result!=null) SignalWorker.schedule(context.applicationContext)
            result
        }
    suspend fun contextSnapshot(now: Long = System.currentTimeMillis()): ContextSnapshot =
        ContextSnapshot(Instant.ofEpochMilli(now).toString(),
            dao.events(now,now+48*60*60*1000L),dao.recentMemories(now,12),
            dao.rules(),dao.recentChats(8),dao.actions(10))
    suspend fun remember(text: String,category: String = "FACT",source: String = "owner"): MemoryRecord {
        require(text.isNotBlank())
        val compact=text.trim().take(8192)
        return database.withTransaction {
            // Owner repetition is not new evidence; keep the existing explicit
            // fact. Inferred repetitions remain distinct for pattern learning.
            if(source=="owner") {
                dao.matchingOwnerMemory(compact,category)?.let {
                    indexOwnerNote(compact)
                    return@withTransaction it
                }
            }
            val entry=MemoryRecord(UUID.randomUUID().toString(),compact,category,source,
                confidence=if(source=="owner") 1.0 else 0.5,explicit=source=="owner")
            dao.putMemory(entry)
            if(source=="owner")indexOwnerNote(compact)
            entry
        }
    }
    private suspend fun indexOwnerNote(text:String) {
        val current=JSONArray(dao.vault("notes")?.value ?: "[]")
        val values=(0 until current.length()).map { current.getString(it) }
        if(values.any { it.equals(text,ignoreCase=true) })return
        dao.putVault(VaultEntry("notes",JSONArray((values+text).takeLast(250)).toString()))
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
        append("\nRecent conversation: ").append(recentConversations.take(4).asReversed()
            .joinToString("; "){it.role+": "+it.body.take(100)})
        // Action arguments are deliberately excluded even from local prompt
        // context; the model only needs to know what actually happened.
        append("\nRecent action outcomes: ").append(recentActions.take(6)
            .joinToString("; "){it.type+"="+it.result})
    }.take(4000)
}
