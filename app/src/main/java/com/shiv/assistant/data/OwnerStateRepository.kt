package com.shiv.assistant.data

import android.content.Context
import androidx.room.withTransaction
import com.shiv.assistant.domain.AssistantConfiguration
import com.shiv.assistant.domain.AuthorityTier
import com.shiv.assistant.domain.ActionKind
import com.shiv.assistant.domain.ActionAuthorization
import com.shiv.assistant.domain.IntelligenceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class OwnerStateSnapshot(
    val config:AssistantConfiguration,val apiKey:String,val speechLocale:String,
    val notificationSummaries:Boolean,val reminderTier:AuthorityTier,
    val chat:List<Pair<String,String>>,val notes:List<String>,val macros:List<String>
)

/** Controller-facing owner state: one repository owns encrypted read/write rules. */
class OwnerStateRepository(context:Context) {
    private val app=context.applicationContext
    private val database=EncryptedDatabase.open(context)
    private val dao=database.dao()
    private val vault=ShivVault(context)
    private fun value(name:String)=runBlocking(Dispatchers.IO) { dao.vault(name)?.value }
    fun load():OwnerStateSnapshot {
        val mode=runCatching { IntelligenceMode.valueOf(value("brain_mode")?:"LOCAL") }
            .getOrDefault(IntelligenceMode.LOCAL)
        val tier=runCatching { AuthorityTier.valueOf(runBlocking(Dispatchers.IO) {
            dao.tier("REMINDER")?.tier ?: "ASK_FIRST"
        }) }.getOrDefault(AuthorityTier.ASK_FIRST).let {
            if(it==AuthorityTier.ACT_THEN_TELL)it else AuthorityTier.ASK_FIRST
        }
        val language=value("speech_locale")?.takeIf { it in setOf("kn-IN","te-IN","en-IN") }?:"en-IN"
        val history=JSONArray(value("history")?:"[]")
        val notes=JSONArray(value("notes")?:"[]")
        val macros=JSONArray(value("macros")?:"[]")
        return OwnerStateSnapshot(
            AssistantConfiguration(mode,value("endpoint")?:"https://api.openai.com/v1/chat/completions",
                value("model")?:"gpt-4.1-mini",value("cloud_consent")=="yes"),
            value("api_secret").orEmpty(),language,value("notification_opt_in")=="yes",tier,
            (0 until history.length()).map {
                val row=history.getJSONObject(it);row.getString("role") to row.getString("body")
            },
            (0 until notes.length()).map { notes.getString(it) },
            (0 until macros.length()).map { macros.getString(it) }
        )
    }
    fun saveSettings(config:AssistantConfiguration,secret:String) = runBlocking(Dispatchers.IO) {
        database.withTransaction {
            dao.putVault(VaultEntry("brain_mode",config.mode.name))
            dao.putVault(VaultEntry("endpoint",config.endpoint))
            dao.putVault(VaultEntry("model",config.model))
            dao.putVault(VaultEntry("cloud_consent",if(config.allowCloudTransmission)"yes" else "no"))
            dao.putVault(VaultEntry("api_secret",secret))
        }
    }
    fun appendChat(role:String,text:String) = runBlocking(Dispatchers.IO) {
        require(role=="user" || role=="assistant")
        database.withTransaction {
            val prior=JSONArray(dao.vault("history")?.value?:"[]")
            val next=JSONArray()
            for(i in (prior.length()-99).coerceAtLeast(0) until prior.length())next.put(prior.get(i))
            next.put(JSONObject().put("role",role).put("body",text.take(12000)))
            dao.putVault(VaultEntry("history",next.toString()))
            dao.putChat(ChatRecord(UUID.randomUUID().toString(),role,text.take(12000)))
        }
    }
    fun clearChat() = runBlocking(Dispatchers.IO) {
        database.withTransaction {
            dao.clearChats()
            dao.clearOwnerChatSignals()
            dao.clearOrphanDecisions()
            dao.putVault(VaultEntry("history","[]"))
        }
    }
    fun saveSpeechLocale(locale:String) = vault.put("speech_locale",locale)
    fun notes():List<String> = vault.notes()
    fun remember(text:String) = runBlocking(Dispatchers.IO) {
        ShivRepository(app).remember(text.trim())
    }
    fun saveMacros(items:List<String>) {
        val json=JSONArray()
        items.takeLast(50).forEach { json.put(it) }
        vault.put("macros",json.toString())
    }
    fun notificationCount():Int=vault.notificationCount()
    fun setNotificationOptIn(enabled:Boolean) {
        vault.put("notification_opt_in",if(enabled)"yes" else "no")
        if(!enabled)vault.clearNotifications()
    }
    suspend fun setReminderTier(tier:AuthorityTier) = dao.putTier(
        PermissionTierRecord("REMINDER",tier.name,System.currentTimeMillis()))
    fun actionTier(kind:ActionKind):AuthorityTier {
        val saved=runBlocking(Dispatchers.IO) { dao.tier(kind.name)?.tier }
        val parsed=runCatching { AuthorityTier.valueOf(saved?:"") }.getOrNull()
        return parsed?.takeIf { it in ActionAuthorization.editableTiers(kind) }
            ?:if(kind==ActionKind.NOTES)AuthorityTier.FREE else AuthorityTier.ASK_FIRST
    }
    suspend fun setActionTier(kind:ActionKind,tier:AuthorityTier) {
        require(kind!=ActionKind.UNKNOWN && tier in ActionAuthorization.editableTiers(kind))
        dao.putTier(PermissionTierRecord(kind.name,tier.name,System.currentTimeMillis()))
    }
    fun lastReminderId():String=vault.get("last_reminder_id").orEmpty()
    fun setLastReminderId(id:String)=vault.put("last_reminder_id",id)
    fun logAction(action:ActionLogRecord) = runBlocking(Dispatchers.IO) { dao.logAction(action) }
    suspend fun actionHistory(limit:Int=50):List<ActionLogRecord> {
        dao.normalizeActionUndo()
        return dao.actions(limit)
    }
    suspend fun consumeActionUndo(id:String,token:String):Boolean =
        dao.consumeActionUndo(id,token)==1
    suspend fun events(begin:Long,end:Long):List<EventRecord> = dao.events(begin,end)
    suspend fun saveQuietRule(hour:Int,minute:Int) = dao.putRule(RuleRecord(
        "quiet-reminders-before","Ignore reminders before "+hour+":"+minute.toString().padStart(2,'0'),
        "reminders_minute_of_day",(hour*60+minute).toString()))
    suspend fun eraseAllOwnerRecords():List<String> = database.withTransaction {
        val pending=dao.reminderIds()
        dao.eraseOwnerRecords()
        pending
    }
}
