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
import com.shiv.assistant.data.ReviewedToolExecutor
import com.shiv.assistant.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
    val actionHistory=mutableStateListOf<com.shiv.assistant.data.ActionLogRecord>()
    suspend fun refreshActionHistory() {
        val current=withContext(Dispatchers.IO) { ownerState.actionHistory() }
        actionHistory.clear()
        actionHistory.addAll(current)
    }
    suspend fun undoAction(log:com.shiv.assistant.data.ActionLogRecord) {
        val token=log.undo.orEmpty()
        if(token.isBlank() || token=="USED")return
        val undone=when {
            token.startsWith("REMINDER:")->ShivReminders.undo(context,token.removePrefix("REMINDER:"))
            token.startsWith("MEMORY:")->{
                val id=token.removePrefix("MEMORY:")
                val exists=com.shiv.assistant.data.EncryptedDatabase.open(context).dao()
                    .memory(id)!=null
                if(exists) {
                    com.shiv.assistant.data.ShivRepository(context).forget(id)
                    notes.clear();notes.addAll(ownerState.notes())
                }
                exists
            }
            token.startsWith("EVENT:")->{
                val id=token.removePrefix("EVENT:")
                val approved=runCatching {
                    com.shiv.assistant.data.CommitmentDecisionRepository(context).undo(id)
                }.getOrNull()!=null
                if(!approved) {
                    val dao=com.shiv.assistant.data.EncryptedDatabase.open(context).dao()
                    if(dao.undoLocalEvent(id)) {
                        ShivReminders.undo(context,"event-"+id)
                        true
                    } else false
                } else {
                    ShivReminders.undo(context,"event-"+id)
                    true
                }
            }
            else->false
        }
        if(undone)ownerState.consumeActionUndo(log.id,token)
        ownerState.logAction(com.shiv.assistant.data.ActionLogRecord(
            java.util.UUID.randomUUID().toString(),"UNDO_"+log.type,"[REDACTED]",
            log.tier,"OWNER_REQUESTED",true,if(undone)"UNDONE" else "UNDO_UNAVAILABLE",
            "NOT_AVAILABLE"))
        notice=if(undone)"Local action reversed" else "This action can no longer be reversed"
        refreshActionHistory()
    }
    var config by mutableStateOf(AssistantConfiguration())
        private set
    var apiKey by mutableStateOf("")
        private set
    var pending by mutableStateOf<ActionProposal?>(null)
        private set
    var pendingTool by mutableStateOf<ParsedToolAction?>(null)
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
    val actionTiers=mutableStateMapOf<ActionKind,AuthorityTier>()
    fun actionTier(kind:ActionKind)=actionTiers[kind]?:ownerState.actionTier(kind)
    suspend fun changeActionTier(kind:ActionKind,tier:AuthorityTier) {
        ownerState.setActionTier(kind,tier)
        actionTiers[kind]=tier
        if(kind==ActionKind.REMINDER)reminderTier=tier
        notice="${kind.name} authority saved: ${tier.name}"
    }
    var speechDraft by mutableStateOf("")
    var postCallSignal by mutableStateOf(vault.get("pending_post_call_signal").orEmpty())
        private set
    fun refreshPostCall() { postCallSignal=vault.get("pending_post_call_signal").orEmpty() }
    suspend fun dismissPostCall() {
        vault.put("pending_post_call_signal","")
        postCallSignal=""
        notice="Post-call check-in dismissed"
    }
    suspend fun answerPostCall(details:String) {
        val answer=details.trim().take(1000)
        if(answer.isBlank())return
        val id=postCallSignal
        if(id.isBlank())return
        phase.submitSignal("post_call_owner_answer","Agreed during call: "+answer,7)
        append("user",answer)
        append("assistant","I saved your answer for review. Open Decisions to confirm any proposed event; nothing was added automatically.")
        dismissPostCall()
    }
    init {
        runCatching {
            val saved=ownerState.load()
            config=saved.config
            apiKey=saved.apiKey
            speechLocale=saved.speechLocale
            notificationSummaries=saved.notificationSummaries
            reminderTier=saved.reminderTier
            ActionKind.entries.filter { it!=ActionKind.UNKNOWN }.forEach {
                actionTiers[it]=ownerState.actionTier(it)
            }
            messages.addAll(saved.chat.map { ChatEntry(it.first,it.second) })
            notes.addAll(saved.notes)
            macros.addAll(saved.macros)
        }.onFailure { notice = "Encrypted data could not be loaded. No information was overwritten." }
        if (messages.isEmpty()) messages.add(ChatEntry("assistant", "SHIV ONLINE. Local intelligence is active. Choose cloud or hybrid mode in Settings if you want an API-powered assistant."))
        runCatching { actionHistory.addAll(runBlocking(Dispatchers.IO) {
            ownerState.actionHistory()
        }) }
    }
    fun settings(mode: IntelligenceMode, endpoint: String, model: String, secret: String, consent: Boolean) {
        require(BuildConfig.NETWORK_ALLOWED || (mode==IntelligenceMode.LOCAL && !consent)) {
            "Private build permits local intelligence only"
        }
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
        changeActionTier(ActionKind.REMINDER,tier)
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
                    true,"CANCEL_REQUESTED","NOT_AVAILABLE"))
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
        if(proposal.kind!=ActionKind.UNKNOWN &&
            ActionAuthorization.decide(proposal,actionTier(proposal.kind),false).permitted) {
            val result=performAction(proposal,false)
            append("assistant",result)
            return
        }
        if (proposal.kind != ActionKind.UNKNOWN) {
            pending = proposal
            append("assistant", proposal.explanation + ". Approve or cancel the proposed action below.")
            return
        }
        working = true
        try {
            var streamPrefix=""
            var structuredStream:Boolean?=null
            val response = if(onLocalFragment!=null && config.mode==IntelligenceMode.LOCAL)
                brain.localBrain.inferStreaming(question) { fragment ->
                    if(structuredStream==null) {
                        streamPrefix+=fragment
                        if(streamPrefix.trimStart().isNotEmpty()) {
                            structuredStream=streamPrefix.trimStart().startsWith("{")
                            if(structuredStream==false) onLocalFragment?.invoke(streamPrefix)
                            streamPrefix=""
                        }
                    } else if(structuredStream==false) {
                        onLocalFragment?.invoke(fragment)
                    }
                }
            else brain.respond(question, notes, config, apiKey)
            // A provider or runtime may return after coroutine cancellation;
            // never append that stale reply to the owner's encrypted history.
            currentCoroutineContext().ensureActive()
            if(response.trimStart().startsWith("{")) {
                val tool=StructuredToolParser.parseOrNull(response.trim())
                when {
                    tool==null -> append("assistant","The model returned an invalid action. No action was taken.")
                    tool.action==ToolAction.STAY_QUIET -> notice="Local model chose to stay quiet"
                    else -> {
                        pendingTool=tool
                        append("assistant","Review the model's proposed "+tool.action.name+" action before it runs.")
                    }
                }
            } else append("assistant", response)
        } catch (cancelled: CancellationException) {
            notice = "Reply canceled"
            throw cancelled
        } catch (e: Exception) {
            append("assistant", "I could not process that request: " + (e.message ?: "unknown error"))
        } finally { working = false }
    }
    fun propose(action: ActionProposal) { pending = action }
    fun rejectTool() {
        val tool=pendingTool ?: return
        pendingTool=null
        notice="Model action cancelled"
        ownerState.logAction(com.shiv.assistant.data.ActionLogRecord(java.util.UUID.randomUUID().toString(),
            tool.action.name,"[REDACTED]","ASK_FIRST","OWNER_DECLINED",false,"CANCELLED","NOT_AVAILABLE"))
        actionHistory.clear()
        actionHistory.addAll(runBlocking(Dispatchers.IO) { ownerState.actionHistory() })
    }
    suspend fun approveTool() {
        val tool=pendingTool ?: return
        pendingTool=null
        try {
            val result=ReviewedToolExecutor(context).execute(tool)
            notice=result
            if(result.startsWith("QUESTION: ")) append("assistant",result.removePrefix("QUESTION: "))
            else append("assistant",tool.action.name+" completed after your approval.")
            refreshActionHistory()
        } catch(e:Exception) {
            notice=e.message ?: "Action failed"
            ownerState.logAction(com.shiv.assistant.data.ActionLogRecord(java.util.UUID.randomUUID().toString(),
                tool.action.name,"[REDACTED]","ASK_FIRST","OWNER_APPROVED",true,
                "FAILED","NOT_AVAILABLE"))
            append("assistant","The reviewed action failed. No completion is claimed.")
            refreshActionHistory()
        }
    }
    fun reject() {
        val action=pending
        pending=null
        notice="Action cancelled"
        if(action!=null) {
            val decision=ActionAuthorization.decide(action,actionTier(action.kind),false)
            ownerState.logAction(ActionAuthorization.log(action,decision,"NOT_REQUESTED","CANCELLED"))
        }
    }
    fun approve() {
        val action = pending ?: return
        pending = null
        val authorization=ActionAuthorization.decide(action,actionTier(action.kind),true)
        if(!authorization.permitted) {
            notice="Action blocked by Shiv owner policy"
            ownerState.logAction(ActionAuthorization.log(action,authorization,
                "OWNER_APPROVED","POLICY_BLOCKED","NOT_AVAILABLE"))
            return
        }
        performAction(action,true)
    }
    private fun performAction(action:ActionProposal,ownerConfirmed:Boolean):String {
        val authorization=ActionAuthorization.decide(action,actionTier(action.kind),ownerConfirmed)
        if(!authorization.permitted)return "Action requires owner review"
        var undo="NOT_AVAILABLE"
        val result = try { when (action.kind) {
            ActionKind.NOTES -> {
                val alreadySaved=ownerState.notes().any {
                    it.equals(action.argument.trim(),ignoreCase=true)
                }
                val saved=runBlocking(Dispatchers.IO) {
                    com.shiv.assistant.data.ShivRepository(context).remember(action.argument)
                }
                notes.clear();notes.addAll(ownerState.notes())
                if(!alreadySaved)undo="MEMORY:"+saved.id
                notice="Encrypted note saved"
                "ENCRYPTED_NOTE_SAVED"
            }
            ActionKind.REMINDER -> {
                val id = ShivReminders.schedule(context, action.argument)
                if(id!=null) {
                    ownerState.setLastReminderId(id.toString())
                    undo="REMINDER:$id"
                }
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
        } } catch (_:Exception) {
            ownerState.logAction(ActionAuthorization.log(action,authorization,
                if(ownerConfirmed)"OWNER_APPROVED" else "OWNER_CONFIGURED",
                "FAILED","NOT_AVAILABLE"))
            notice="Action failed; no completion is claimed"
            return notice
        }
        ownerState.logAction(ActionAuthorization.log(action,authorization,
            if(ownerConfirmed)"OWNER_APPROVED" else "OWNER_CONFIGURED",result,undo))
        runCatching {
            actionHistory.clear()
            actionHistory.addAll(runBlocking(Dispatchers.IO) { ownerState.actionHistory() })
        }
        return when(result) {
            "ENCRYPTED_NOTE_SAVED"->"Saved to encrypted Shiv memory."
            "SCHEDULED"->"Reminder scheduled locally. You can undo it in Permissions."
            "INVALID_DATE"->"I couldn't parse the reminder time. Try 'remind me in 10 minutes'."
            "APP_HANDOFF_OPENED"->"Opened an external app for your review; Shiv cannot verify completion there."
            else->"No compatible app was found or the action failed."
        }
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
            actionTiers.clear()
            actionHistory.clear()
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
        actionTiers.clear()
        actionHistory.clear()
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
