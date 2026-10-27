package com.shiv.assistant.data

import android.content.Context
import androidx.room.withTransaction
import com.shiv.assistant.ai.LocalBackend
import com.shiv.assistant.ai.LocalInferenceSettings
import com.shiv.assistant.domain.AppSourceAccess
import com.shiv.assistant.domain.AppSourceMode
import com.shiv.assistant.domain.SourceExclusions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** Encrypted Phase 1 writes. Platform permissions remain in the caller. */
class PhaseOneDataRepository(context:Context) {
    private val database=EncryptedDatabase.open(context)
    private val dao=database.dao()
    private val vault=ShivVault(context)

    fun localBackend():LocalBackend=LocalInferenceSettings.backend(
        vault.get(LocalInferenceSettings.BACKEND_KEY))
    fun localOutputTokens():Int=LocalInferenceSettings.outputTokens(
        vault.get(LocalInferenceSettings.OUTPUT_TOKENS_KEY))
    fun setLocalBackend(value:LocalBackend) = vault.put(LocalInferenceSettings.BACKEND_KEY,value.name)
    fun setLocalOutputTokens(value:Int) = vault.put(LocalInferenceSettings.OUTPUT_TOKENS_KEY,value.toString())
    fun proactiveOptIn():Boolean=vault.get("proactive_opt_in")=="yes"
    fun setProactiveOptIn(enabled:Boolean)=vault.put("proactive_opt_in",if(enabled)"yes" else "no")
    fun setScreenAllowedPackage(value:String)=vault.put("screen_allowed_package",value)

    suspend fun importCalendar(event:EventRecord) = database.withTransaction {
        dao.putEvent(event)
        dao.logAction(ActionLogRecord("calendar-"+event.id,
            "IMPORT_CALENDAR","[REDACTED]","ASK_FIRST","OWNER_APPROVED",
            true,"ENCRYPTED_EVENT_IMPORTED",null))
    }
    suspend fun currentEvent(id:String):EventRecord?=dao.event(id)
    fun exportedCalendarEvent(id:String):Boolean=!vault.get("calendar_export_"+id).isNullOrBlank()
    fun markCalendarExport(id:String,calendarId:Long,externalId:Long) =
        vault.put("calendar_export_"+id,"$calendarId:$externalId")
    suspend fun logCalendarExport():Boolean = try {
        dao.logAction(ActionLogRecord(UUID.randomUUID().toString(),"EXPORT_CALENDAR",
            "[REDACTED]","ASK_FIRST","OWNER_APPROVED",true,"EXTERNAL_EVENT_CREATED",null))
        true
    } catch (_:Exception) { false }

    /** Returns whether an armed screen window was revoked. */
    fun addDeniedPackage(packageName:String):Boolean {
        val value=packageName.trim().lowercase(java.util.Locale.ROOT)
        require(SourceExclusions.validPackage(value)) { "Enter a full Android app package" }
        val old=SourceExclusions.fromString(vault.get("app_denied_packages"))
        require(old.size<100 || value in old) { "Denylist capacity reached" }
        vault.put("app_denied_packages",(old+value).sorted().joinToString("\n"))
        if(vault.get("screen_allowed_package")!=value)return false
        vault.put("screen_allowed_package","")
        return true
    }
    fun removeDeniedPackage(packageName:String) {
        val old=SourceExclusions.fromString(vault.get("app_denied_packages"))
        vault.put("app_denied_packages",
            (old-packageName.trim().lowercase(java.util.Locale.ROOT)).sorted().joinToString("\n"))
    }
    fun setAppSourceMode(packageName:String,mode:AppSourceMode) {
        val name=packageName.trim().lowercase(java.util.Locale.ROOT)
        require(SourceExclusions.validPackage(name)) { "Enter a full Android app package" }
        val current=AppSourceAccess.decode(vault.get(AppSourceAccess.VAULT_KEY))
        require(current.size<100 || name in current) { "App policy capacity reached" }
        vault.put(AppSourceAccess.VAULT_KEY,AppSourceAccess.encode(current+(name to mode)))
    }
    fun removeAppSourceMode(packageName:String) {
        val current=AppSourceAccess.decode(vault.get(AppSourceAccess.VAULT_KEY))
        vault.put(AppSourceAccess.VAULT_KEY,AppSourceAccess.encode(
            current-packageName.trim().lowercase(java.util.Locale.ROOT)))
    }
    suspend fun saveReviewedTask(title:String,dueAt:Long) = withContext(Dispatchers.IO) {
        database.withTransaction {
            dao.putTask(TaskRecord(UUID.randomUUID().toString(),title.trim(),"OPEN",dueAt))
            dao.logAction(ActionLogRecord(UUID.randomUUID().toString(),
                "OCR_TASK","[REDACTED]","ASK_FIRST","OWNER_APPROVED",true,
                "ENCRYPTED_TASK_SAVED",null))
        }
    }
    suspend fun retryFailedSignals():Int=dao.retryFailedSignals()
}
