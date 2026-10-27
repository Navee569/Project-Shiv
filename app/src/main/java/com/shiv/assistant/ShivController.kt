package com.shiv.assistant

import android.content.Context
import android.net.Uri
import android.provider.Settings
import android.content.Intent
import androidx.compose.runtime.*
import com.shiv.assistant.ai.ShivIntelligence
import com.shiv.assistant.automation.ShivActions
import com.shiv.assistant.automation.ShivReminders
import com.shiv.assistant.data.ShivVault
import com.shiv.assistant.data.OwnerStateRepository
import com.shiv.assistant.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import androidx.room.withTransaction

data class ChatEntry(val role: String, val text: String)

/**
 * State and behavior belong to Shiv, not a renamed third-party App class.
 * External operations are always placed in a review queue first.
 */
class ShivController(private val context: Context) {
    val vault = ShivVault(context)
    private val ownerState=OwnerStateRepository(context)
    private val brain = ShivIntelligence(context)
    val phase = PhaseOneController(context, brain.localBrain)
    var speechLocale by mutableStateOf("en-IN")
        private set
    fun updateSpeechLocale(value: String) {
        require(value in setOf("kn-IN","te-IN","en-IN"))
        speechLocale = value
        ownerState.saveSpeechLocale(value)
    }
    fun showNotice(text: String) { notice = text }
    fun notificationSummaryCount():Int=ownerState.notificationCount()
    suspend fun exportOwnerArchive(uri:Uri,secret:CharArray):Int =
        com.shiv.assistant.data.PortableOwnerArchive(context).exportTo(uri,secret)
    suspend fun restoreOwnerArchive(uri:Uri,secret:CharArray):Int {
        val count=com.shiv.assistant.data.PortableOwnerArchive(context).restoreFrom(uri,secret)
        afterPortableRestore(count)
        return count
    }
    val messages = mutableStateListOf<ChatEntry>()
    val notes = mutableStateListOf<String>()
    val macros = mutableStateListOf<String>()
    var config by mutableStateOf(AssistantConfiguration())
        private set
    var apiKey by mutableStateOf("")
        private set
    var pending by mutableStateOf<ActionProposal?>(null)
        private set
    var visionCandidate by mutableStateOf<Uri?>(null)
        private set
    var ocrPreview by mutableStateOf("")
        private set
    var ocrDueDate by mutableStateOf<com.shiv.assistant.domain.DueDateProposal?>(null)
        private set
    var working by mutableStateOf(false)
        private set
    var notice by mutableStateOf("Local-first • Encrypted • You control every action")
        private set
    var notificationSummaries by mutableStateOf(false)
        private set
    var reminderTier by mutableStateOf(AuthorityTier.ASK_FIRST)
    var speechDraft by mutableStateOf("")
    init {
        runCatching {
            val saved=ownerState.load()
            config=saved.config
            apiKey=saved.apiKey
            speechLocale=saved.speechLocale
            notificationSummaries=saved.notificationSummaries
            reminderTier=saved.reminderTier
            messages.addAll(saved.chat.map { ChatEntry(it.first,it.second) })
            notes.addAll(saved.notes)
            macros.addAll(saved.macros)
        }.onFailure { notice = "Encrypted data could not be loaded. No information was overwritten." }
        if (messages.isEmpty()) messages.add(ChatEntry("assistant", "SHIV ONLINE. Local intelligence is active. Choose cloud or hybrid mode in Settings if you want an API-powered assistant."))
    }
    fun settings(mode: IntelligenceMode, endpoint: String, model: String, secret: String, consent: Boolean) {
        require(CloudSafety.endpointAllowed(endpoint)) { "Use an HTTPS endpoint or explicit local loopback address" }
        require(model.isNotBlank()) { "Model name cannot be empty" }
        val proposed=AssistantConfiguration(mode, endpoint.trim(), model.trim(), consent)
        ownerState.saveSettings(proposed,secret)
        config=proposed
        apiKey = secret
        notice = "Intelligence configuration saved securely"
    }
    suspend fun changeReminderTier(tier:AuthorityTier) {
        require(tier in setOf(AuthorityTier.ASK_FIRST,AuthorityTier.ACT_THEN_TELL))
        ownerState.setReminderTier(tier)
        reminderTier=tier
        notice=if(tier==AuthorityTier.ASK_FIRST) "Reminder approval required"
            else "Explicit reminders may be scheduled, with owner undo available"
    }
    suspend fun undoLatestReminder() {
        val id=ownerState.lastReminderId()
        if(id.isBlank()) {
            notice="No pending reminder to undo"
            return
        }
        val undone=ShivReminders.undo(context,id)
        if(undone) {
            ownerState.setLastReminderId("")
            ownerState.logAction(
                    com.shiv.assistant.data.ActionLogRecord(java.util.UUID.randomUUID().toString(),
                    "REMINDER_UNDO","[REDACTED]","ASK_FIRST","OWNER_REQUESTED",
                    true,"CANCEL_REQUESTED",null))
        }
        notice=if(undone) "Pending reminder canceled; delivered notifications cannot be recalled"
            else "Reminder already delivered, canceled, or missing"
    }
    private fun append(role: String, text: String) {
        ownerState.appendChat(role,text)
        messages.add(ChatEntry(role, text))
    }
    suspend fun submit(message: String,onLocalFragment:(suspend (String)->Unit)?=null) {
        val question = message.trim()
        if (question.isEmpty() || working) return
        append("user", question)
        // Refresh after owner edits from the Memories screen; never use stale facts.
        notes.clear()
        notes.addAll(ownerState.notes())
        // Durable, encrypted signal is saved BEFORE any model inference.
        phase.submitSignal("owner_input", question, 5)
        // Precise scheduling/DB questions never require model hallucination.
        val lower=question.lowercase().trimEnd('?', '.', '!')
        if(lower=="what's on tomorrow" || lower=="what is on tomorrow") {
            val day=java.time.LocalDate.now().plusDays(1)
            val zone=java.time.ZoneId.systemDefault()
            val begin=day.atStartOfDay(zone).toInstant().toEpochMilli()
            val end=day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()-1
            val events=ownerState.events(begin,end)
            append("assistant", if(events.isEmpty())"There are no Shiv-recorded events tomorrow."
                else events.joinToString("\n"){it.title+" at "+
                    java.time.Instant.ofEpochMilli(it.startAt).atZone(zone).toLocalTime()})
            return
        }
        val before=Regex("(?i)^ignore reminders before\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)$")
            .matchEntire(question.trim().trimEnd('.', '!'))
        if(before!=null) {
            val hour=(before.groupValues[1].toInt()%12)+
                if(before.groupValues[3].equals("pm",true))12 else 0
            val minute=before.groupValues[2].toIntOrNull()?:0
            if(hour !in 0..23 || minute !in 0..59) {
                append("assistant","Please use a valid time such as 9 AM.")
            } else {
                ownerState.saveQuietRule(hour,minute)
                append("assistant","Saved. Reminders scheduled before that local time will be deferred.")
            }
            return
        }
        val proposal = ShivPlanner.parse(question)
        if (proposal.kind == ActionKind.NOTES) {
            saveNote(proposal.argument)
            append("assistant", "Saved to encrypted Shiv memory.")
            return
        }
        if (proposal.kind == ActionKind.REMINDER &&
            reminderTier == AuthorityTier.ACT_THEN_TELL &&
            ActionAuthorization.decide(proposal, reminderTier, false).permitted) {
            val id=ShivReminders.schedule(context,proposal.argument)
            if(id!=null) ownerState.setLastReminderId(id.toString())
            ownerState.logAction(
                    com.shiv.assistant.data.ActionLogRecord(java.util.UUID.randomUUID().toString(),
                        "REMINDER","[REDACTED]","ACT_THEN_TELL","OWNER_CONFIGURED",
                        id!=null,if(id!=null)"SCHEDULED" else "INVALID_DATE",null))
            append("assistant",if(id!=null) "Reminder scheduled. Use Permissions > Undo latest reminder to cancel it."
                else "I couldn't parse the reminder time. Try 'remind me in 10 minutes'.")
            return
        }
        if (proposal.kind != ActionKind.UNKNOWN) {
            pending = proposal
            append("assistant", proposal.explanation + ". Approve or cancel the proposed action below.")
            return
        }
        working = true
        try {
            val response = if(onLocalFragment!=null && config.mode==IntelligenceMode.LOCAL)
                brain.localBrain.inferStreaming(question,onLocalFragment)
            else brain.respond(question, notes, config, apiKey)
            // A provider or runtime may return after coroutine cancellation;
            // never append that stale reply to the owner's encrypted history.
            currentCoroutineContext().ensureActive()
            append("assistant", response)
        } catch (cancelled: CancellationException) {
            notice = "Reply canceled"
            throw cancelled
        } catch (e: Exception) {
            append("assistant", "I could not process that request: " + (e.message ?: "unknown error"))
        } finally { working = false }
    }
    fun propose(action: ActionProposal) { pending = action }
    fun reject() {
        val action=pending
        pending=null
        notice="Action cancelled"
        if(action!=null) {
            val decision=ActionAuthorization.decide(action,null,false)
            ownerState.logAction(ActionAuthorization.log(action,decision,"NOT_REQUESTED","CANCELLED"))
        }
    }
    fun approve() {
        val action = pending ?: return
        pending = null
        val authorization=ActionAuthorization.decide(action,null,true)
        if(!authorization.permitted) {
            notice="Action blocked by Shiv owner policy"
            return
        }
        val result = when (action.kind) {
            ActionKind.NOTES -> {
                saveNote(action.argument)
                "ENCRYPTED_NOTE_SAVED"
            }
            ActionKind.REMINDER -> {
                val id = ShivReminders.schedule(context, action.argument)
                if(id!=null) ownerState.setLastReminderId(id.toString())
                notice = if (id == null) "Use: remind me in 10 minutes to ..."
                    else "Reminder scheduled locally; undo is available in Permissions"
                if(id==null) "INVALID_DATE" else "SCHEDULED"
            }
            else -> {
                val opened=ShivActions.launch(context, action)
                notice = if(opened) "Opened external app for review"
                    else "No compatible app installed, or action unavailable"
                if(opened) "APP_HANDOFF_OPENED" else "APP_HANDOFF_FAILED"
            }
        }
        ownerState.logAction(ActionAuthorization.log(action,authorization,"APP_HANDOFF",result))
    }
    fun saveNote(value: String) {
        if (value.trim().isEmpty()) return
        ownerState.remember(value.trim())
        notes.clear(); notes.addAll(ownerState.notes())
        notice = "Encrypted note saved"
    }
    fun saveMacro(goal: String) {
        if (goal.isBlank()) return
        val next=(macros+goal.trim().take(512)).takeLast(50)
        ownerState.saveMacros(next)
        macros.clear();macros.addAll(next)
        notice = "Routine saved — execution always requires review"
    }
    /**
     * Irreversible owner-requested clearing of ALL private records, including
     * consent and cloud secret, in a single SQLCipher transaction. Imported
     * public model files are intentionally retained; this is not a device wipe.
     */
    suspend fun eraseOwnerRecords() {
        context.stopService(android.content.Intent(
            context,com.shiv.assistant.voice.ShivMicSessionService::class.java))
        require(!working) { "Finish the current action first" }
        working=true
        try {
            // Stop external producers BEFORE touching the private database.
            check(context.getSharedPreferences("shiv_awareness_control",Context.MODE_PRIVATE)
                .edit().clear().commit()) { "Could not revoke sensing" }
            check(context.getSharedPreferences("shiv_learning",Context.MODE_PRIVATE)
                .edit().clear().commit()) { "Could not revoke passive learning" }
            check(context.getSharedPreferences("shiv_call_check_in",Context.MODE_PRIVATE)
                .edit().clear().commit()) { "Could not revoke call check-ins" }
            val ids=withContext(Dispatchers.IO) { ownerState.eraseAllOwnerRecords() }
            val wm=androidx.work.WorkManager.getInstance(context)
            for(id in ids) {
                wm.cancelAllWorkByTag("shiv-reminder-"+id)
                wm.cancelUniqueWork("shiv-approved-"+id)
            }
            wm.cancelUniqueWork("shiv-durable-signals")
            wm.cancelUniqueWork("shiv-opt-in-pattern-learning")
            wm.cancelUniqueWork("shiv-opt-in-agenda-nudges")
            phase.hub.clearSessions()
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
                .cancelAll()
            messages.clear()
            notes.clear()
            macros.clear()
            pending=null
            visionCandidate=null
            clearOcrPreview()
            apiKey=""
            config=AssistantConfiguration()
            speechLocale="en-IN"
            notificationSummaries=false
            reminderTier=AuthorityTier.ASK_FIRST
            phase.discardCalendarPreview()
            phase.clearContactNames()
            phase.reload()
            phase.refreshPrivacyAfterOwnerReset()
            notice="Owner data erased from Shiv's encrypted database; imported model files remain."
        } finally {
            working=false
        }
    }

    /**
     * UI refresh after owner-initiated portable restore. Existing sessions and
     * cloud grants must not survive import of another device's encrypted data.
     * The imported cloud API secret remains encrypted but is NOT enabled.
     */
    suspend fun afterPortableRestore(count:Int) {
        context.stopService(android.content.Intent(
            context,com.shiv.assistant.voice.ShivMicSessionService::class.java))
        com.shiv.assistant.automation.ShivAgendaNudgeWorker
            .updateRegistration(context,false)
        phase.hub.clearSessions()
        messages.clear()
        notes.clear()
        macros.clear()
        pending=null
        visionCandidate=null
        clearOcrPreview()
        apiKey=""
        config=AssistantConfiguration()
        reminderTier=AuthorityTier.ASK_FIRST
        notificationSummaries=false
        speechLocale="en-IN"
        phase.discardCalendarPreview()
        phase.clearContactNames()
        phase.reload()
        phase.refreshPrivacyAfterOwnerReset()
        notice="Restored "+count+" encrypted records. Cloud and old reminders remain disabled; restart Shiv to review settings."
    }

    fun clearHistory() {
        ownerState.clearChat()
        messages.clear()
        notice = "Chat history erased"
    }
    fun notifications(on: Boolean) {
        notificationSummaries = on
        ownerState.setNotificationOptIn(on)
        context.getSharedPreferences("shiv_awareness_control", Context.MODE_PRIVATE).edit()
            .putBoolean("notifications_enabled", on).apply()
        com.shiv.assistant.awareness.AwarenessControl.show(context)
    }
    fun chooseImage(uri: Uri?) {
        visionCandidate = uri
        clearOcrPreview()
    }
    fun cancelImage() { visionCandidate = null }
    fun clearOcrPreview() {
        ocrPreview=""
        ocrDueDate=null
    }
    suspend fun recognizeImageTextOffline() {
        val selected=visionCandidate ?: return
        visionCandidate=null
        working=true
        try {
            val found=com.shiv.assistant.ai.OfflineLatinOcr(context).extract(selected)
            // Deliberately reject possible credentials before storing even
            // a transient Compose/UI state. The selected image is not copied.
            if(Regex("(?i)\\b(otp|password|passcode|pin|cvv|verification code)\\b")
                .containsMatchIn(found) || Regex("\\b[0-9]{8,}\\b").containsMatchIn(found)) {
                clearOcrPreview()
                notice="OCR content may contain credentials; preview suppressed"
            } else {
                ocrPreview=found.take(2000).ifBlank { "No readable Latin text detected" }
                ocrDueDate=com.shiv.assistant.domain.OcrDueDatePolicy.propose(found)
                notice="Offline OCR preview only; no text saved or sent to cloud"
            }
        } catch (_:Exception) {
            clearOcrPreview()
            notice="Offline Latin OCR unavailable for the selected image"
        } finally {working=false}
    }
    suspend fun approveOcrDueTask(title:String,clock:String) {
        val candidate=ocrDueDate ?: return
        if(working)return
        working=true
        try {
            phase.saveOwnerReviewedOcrTask(candidate,title,clock)
            clearOcrPreview()
            notice="Owner-confirmed OCR task saved in encrypted agenda"
        } catch (_:Exception) {
            notice="Task not saved. Check title, future date and 24-hour time."
        } finally {working=false}
    }
    suspend fun labelImageLocally() {
        val uri=visionCandidate?:return
        visionCandidate=null
        working=true
        try {
            val labels=com.shiv.assistant.ai.OfflineImageLabels(context).labels(uri)
            append("assistant",if(labels.isEmpty()) "No confident on-device image labels found."
                else "On-device image labels (not OCR or a full scene description): "+labels.joinToString(", "))
        } catch(e:Exception) { notice="Offline labels failed: "+(e.message?:"unknown error") }
        finally { working=false }
    }
    suspend fun approveVision() {
        val uri = visionCandidate ?: return
        visionCandidate = null
        working = true
        try {
            val description = brain.describeImage(uri, config, apiKey)
            append("assistant", "Image analysis: " + description)
        } catch (e: Exception) {
            notice = "Vision request unavailable: " + (e.message ?: "unknown error")
        } finally { working = false }
    }
    suspend fun importText(uri: Uri) {
        try {
            val text = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                    val value = it.readText()
                    require(value.length <= 100_000) { "Document exceeds 100 KB text limit" }
                    value
                } ?: error("Cannot read file")
            }
            saveNote(text.take(8192))
        } catch (e: Exception) { notice = "Import failed: " + (e.message ?: "unknown error") }
    }
    fun openNotificationSettings() {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
