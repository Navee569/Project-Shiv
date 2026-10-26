package com.shiv.assistant

import android.content.Context
import android.net.Uri
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import com.shiv.assistant.voice.ShivMicSessionService
import android.content.Intent
import com.shiv.assistant.integrations.CalendarAdapter
import com.shiv.assistant.integrations.CalendarCandidate
import com.shiv.assistant.integrations.CalendarImportPolicy
import com.shiv.assistant.integrations.ContactsAdapter
import com.shiv.assistant.domain.SensitiveAppDenylist
import com.shiv.assistant.domain.SourceExclusions
import com.shiv.assistant.domain.DueDateProposal
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import com.shiv.assistant.awareness.ScreenInspectionPolicy
import androidx.compose.runtime.*
import com.shiv.assistant.ai.*
import com.shiv.assistant.automation.SignalWorker
import com.shiv.assistant.automation.ShivAgendaNudgeWorker
import com.shiv.assistant.data.OwnerNudgeLedger
import com.shiv.assistant.data.OwnerNudgeReaction
import com.shiv.assistant.data.LocalModelComputeLedger
import com.shiv.assistant.data.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Live developer panels are honest about imported, loaded, and missing models. */
class PhaseOneController(private val context: Context, val hub: ShivBrainHub) {
    private val repository=ShivRepository(context)
    private val dao=EncryptedDatabase.open(context).dao()
    val models=mutableStateListOf<ModelRecord>()
    val memories=mutableStateListOf<MemoryRecord>()
    val upcoming=mutableStateListOf<EventRecord>()
    val upcomingTasks=mutableStateListOf<TaskRecord>()
    val beliefs=mutableStateListOf<BeliefRecord>()
    val decisions=mutableStateListOf<DecisionTraceRecord>()
    val calendarCandidates=mutableStateListOf<CalendarCandidate>()
    val contactNameMatches=mutableStateListOf<String>()
    val deniedPackages=mutableStateListOf<String>()
    private val nudgeLedger=OwnerNudgeLedger(ShivVault(context))
    private val computeLedger=LocalModelComputeLedger(ShivVault(context))
    var localBudgetSeconds by mutableIntStateOf(computeLedger.budgetSeconds())
        private set
    var proactiveOptIn by mutableStateOf(ShivVault(context).get("proactive_opt_in")=="yes")
        private set
    var proactiveFeedback by mutableStateOf(nudgeLedger.report())
        private set
    var proactiveLastKey by mutableStateOf(nudgeLedger.lastKey())
        private set
    var candidate by mutableStateOf<Uri?>(null)
        private set
    var progress by mutableStateOf("No model import in progress")
        private set
    var loading by mutableStateOf(false)
        private set
    var issue by mutableStateOf<String?>(null)
        private set
    var notificationCount by mutableIntStateOf(0)
        private set
    fun openAccessibilitySettings() {
        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    fun requestOneShotAwareness(packageName:String) {
        val allowed=packageName.trim().lowercase()
        if(!ScreenInspectionPolicy.permits(allowed, allowed)) {
            issue="Enter one non-sensitive, fully qualified Android package"
            return
        }
        // The exact allowed source is persisted encrypted; a broad Accessibility
        // grant does not authorize Shiv to traverse other apps.
        ShivVault(context).put("screen_allowed_package", allowed)
        check(context.getSharedPreferences("shiv_awareness_control",Context.MODE_PRIVATE).edit()
            .putBoolean("owner_enabled",true).putBoolean("paused",false)
            .putLong("capture_until",System.currentTimeMillis()+15_000L).commit())
        issue=null
        progress="One-shot capture armed for 15 seconds, ONLY for "+allowed
    }
    fun stopOneShotAwareness() {
        context.getSharedPreferences("shiv_awareness_control",Context.MODE_PRIVATE).edit()
            .remove("capture_until").putBoolean("paused",true).commit()
        ShivVault(context).put("screen_allowed_package","")
        progress="One-shot screen inspection stopped"
    }
    suspend fun readPhoneCalendar() {
        calendarCandidates.clear()
        try {
            val preview=CalendarAdapter(context).inspectNext48Hours()
            calendarCandidates.addAll(preview)
            progress=preview.size.toString()+" candidate(s) previewed. Nothing imported until you approve."
        } catch(e:Exception) {
            issue="Calendar preview unavailable; check permission"
        }
    }
    suspend fun confirmCalendarImport(id:String) {
        val candidate=calendarCandidates.firstOrNull { it.id==id } ?: run {
            issue="Calendar selection expired; preview again"
            return
        }
        if(context.checkSelfPermission(Manifest.permission.READ_CALENDAR)!=PackageManager.PERMISSION_GRANTED) {
            issue="Calendar permission revoked; import stopped"
            calendarCandidates.clear()
            return
        }
        val approved=CalendarImportPolicy.accept(candidate,System.currentTimeMillis()) ?: run {
            calendarCandidates.remove(candidate)
            issue="Invalid or expired calendar candidate"
            return
        }
        withContext(Dispatchers.IO) {
            val database=EncryptedDatabase.open(context)
            database.dao().putEvent(approved)
            database.dao().logAction(ActionLogRecord("calendar-"+approved.id,
                "IMPORT_CALENDAR","[REDACTED]","ASK_FIRST","OWNER_APPROVED",
                true,"ENCRYPTED_EVENT_IMPORTED",null))
        }
        calendarCandidates.remove(candidate)
        progress="Owner-approved calendar event saved in encrypted Shiv storage"
        reload()
    }
    fun discardCalendarPreview() { calendarCandidates.clear();progress="Calendar preview discarded" }
    suspend fun lookupContactNames(query:String) {
        contactNameMatches.clear()
        try {
            val found=ContactsAdapter(context).matchNames(query)
            contactNameMatches.addAll(found)
            progress=found.size.toString()+" matching display name(s); never saved or sent to AI"
        } catch (_:Exception) {
            issue="Contact lookup unavailable or permission denied"
        }
    }
    fun clearContactNames() { contactNameMatches.clear() }
    suspend fun addDeniedPackage(packageName:String) = withContext(Dispatchers.IO) {
        val value=packageName.trim().lowercase(java.util.Locale.ROOT)
        require(SourceExclusions.validPackage(value)) { "Enter a full Android app package" }
        val vault=ShivVault(context)
        val old=SourceExclusions.fromString(vault.get("app_denied_packages"))
        require(old.size < 100 || value in old) { "Denylist capacity reached" }
        vault.put("app_denied_packages",(old+value).sorted().joinToString("\n"))
        // Any currently armed window for this app is revoked immediately.
        if(vault.get("screen_allowed_package")==value) {
            vault.put("screen_allowed_package","")
            context.getSharedPreferences("shiv_awareness_control",Context.MODE_PRIVATE)
                .edit().remove("capture_until").putBoolean("paused",true).commit()
        }
        reload()
        progress="App source excluded before notification or screen capture"
    }
    suspend fun removeDeniedPackage(packageName:String) = withContext(Dispatchers.IO) {
        val vault=ShivVault(context)
        val old=SourceExclusions.fromString(vault.get("app_denied_packages"))
        vault.put("app_denied_packages",
            (old-packageName.trim().lowercase(java.util.Locale.ROOT)).sorted().joinToString("\n"))
        reload()
        progress="Custom exclusion removed; built-in banking denylist remains enforced"
    }
    fun startOfflineMicSession(locale:String) {
        if(Build.VERSION.SDK_INT<33 ||
            !android.speech.SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            issue="Offline on-device speech service unavailable. No cloud recognizer was started."
            return
        }
        if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=
            PackageManager.PERMISSION_GRANTED) {
            issue="Grant microphone permission before starting an owner-driven session"
            return
        }
        if(locale !in setOf("en-IN","kn-IN","te-IN")) {
            issue="Choose Kannada, Telugu or English"
            return
        }
        try {
            context.startForegroundService(Intent(context,ShivMicSessionService::class.java)
                .setAction(ShivMicSessionService.ACTION_START)
                .putExtra(ShivMicSessionService.EXTRA_LOCALE,locale))
            issue=null
            progress="Owner-started offline microphone session requested. Notification STOP available; expires within 10 minutes."
        } catch (_:Exception) {
            issue="Android blocked the microphone session. Start it with Shiv in foreground."
        }
    }
    fun stopOfflineMicSession() {
        context.stopService(Intent(context,ShivMicSessionService::class.java))
        progress="Offline microphone session stopped"
    }
    fun setLocalComputeBudget(seconds:Int) {
        require(seconds in 60..3600)
        computeLedger.setBudgetSeconds(seconds)
        localBudgetSeconds=seconds
        progress="Local model daily compute allowance: "+(seconds/60)+" min. " +
            "This is not a measured battery budget."
    }
    fun localComputeReport():String=computeLedger.report()
    fun setProactiveOptIn(enabled:Boolean) {
        ShivVault(context).put("proactive_opt_in",if(enabled)"yes" else "no")
        proactiveOptIn=enabled
        ShivAgendaNudgeWorker.updateRegistration(context,enabled)
        progress=if(enabled) "Owner-enabled, quiet-hour-limited optional agenda notices"
            else "Optional agenda notices disabled"
    }
    fun recordProactiveReaction(reaction:OwnerNudgeReaction) {
        if(nudgeLedger.lastKey().isBlank()) {
            issue="No optional agenda notification exists to review"
            return
        }
        nudgeLedger.react(reaction)
        proactiveLastKey=nudgeLedger.lastKey()
        proactiveFeedback=nudgeLedger.report()
        progress="Feedback saved encrypted. Only optional future interruption caps may decrease."
    }
    fun refreshProactiveFeedback() {
        proactiveLastKey=nudgeLedger.lastKey()
        proactiveFeedback=nudgeLedger.report()
    }
    fun setPatternLearning(on:Boolean) {
        context.getSharedPreferences("shiv_learning",Context.MODE_PRIVATE).edit()
            .putBoolean("owner_enabled",on).apply()
        if(on) com.shiv.assistant.automation.PatternWorker.enable(context)
        else androidx.work.WorkManager.getInstance(context)
            .cancelUniqueWork("shiv-opt-in-pattern-learning")
        progress=if(on)"Charging-only hypothesis learning enabled" else "Passive learning disabled"
    }
    fun enablePostCallCheckIn(on:Boolean) {
        context.getSharedPreferences("shiv_call_check_in",Context.MODE_PRIVATE).edit()
            .putBoolean("owner_enabled",on).apply()
        progress=if(on)"Post-call check-in metadata enabled" else "Post-call checks disabled"
    }
    fun selectModel(uri:Uri?) {
        candidate=uri
        if(uri!=null)runCatching {
            context.contentResolver.takePersistableUriPermission(uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    fun cancelModel() { candidate=null }
    suspend fun saveOwnerReviewedOcrTask(
        proposal:DueDateProposal, title:String,clock:String
    ) {
        require(title.trim().length in 3..180) { "Task title must contain 3–180 characters" }
        require(clock.matches(Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]"))) {
            "Choose an explicit 24-hour time"
        }
        val time=LocalTime.parse(clock)
        val dueAt=proposal.date.atTime(time).atZone(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        require(dueAt>System.currentTimeMillis()) { "Task time must be in the future" }
        withContext(Dispatchers.IO) {
            val id=UUID.randomUUID().toString()
            dao.putTask(TaskRecord(id,title.trim(),"OPEN",dueAt))
            dao.logAction(ActionLogRecord(UUID.randomUUID().toString(),
                "OCR_TASK","[REDACTED]","ASK_FIRST","OWNER_APPROVED",true,
                "ENCRYPTED_TASK_SAVED",null))
        }
        progress="Owner-confirmed offline OCR task saved to encrypted Shiv agenda"
        reload()
    }
    suspend fun reload() = withContext(Dispatchers.IO) {
        val stored=dao.models()
        val events=repository.upcoming(System.currentTimeMillis(),System.currentTimeMillis()+48*3600_000L)
        val tasks=dao.pendingTasks()
        val storedBeliefs=dao.beliefs()
        val traces=dao.traces(30)
        val count=dao.pendingCount()
        val savedMemories=dao.recentMemories(System.currentTimeMillis(),100)
        val excluded=SourceExclusions.fromString(ShivVault(context).get("app_denied_packages"))
        withContext(Dispatchers.Main) {
            models.clear();models.addAll(stored)
            memories.clear();memories.addAll(savedMemories)
            upcoming.clear();upcoming.addAll(events)
            upcomingTasks.clear();upcomingTasks.addAll(tasks)
            beliefs.clear();beliefs.addAll(storedBeliefs)
            decisions.clear();decisions.addAll(traces)
            notificationCount=count
            deniedPackages.clear();deniedPackages.addAll(excluded.sorted())
        }
    }
    suspend fun importModel(role:String) {
        val uri=candidate?:return
        loading=true; issue=null
        try {
            val record=ModelImporter(context).import(uri,role) { p ->
                progress="Copied "+(p.bytesCopied/(1024*1024))+" MB of "+(p.totalBytes/(1024*1024))+" MB"
            }
            progress="Imported "+record.name+" with SHA-256 "+record.sha256.take(12)+"…"
            candidate=null
            reload()
        } catch(e:Exception) { issue=e.message?:"Model import failed" }
        finally { loading=false }
    }
    suspend fun loadModel(record:ModelRecord) {
        loading=true;issue=null
        try {
            val lease=ModelGovernor(context).lease(record.role)
            require(lease.allowed) { lease.reason }
            require(withContext(Dispatchers.IO) {
                com.shiv.assistant.ai.ModelIntegrity.valid(File(record.uri),record.bytes,record.sha256)
            }) {
                "Imported model checksum or size changed. Reimport a trusted model file."
            }
            hub.load(record.role, File(record.uri), lease.freeRamBytes)
            progress="Model loaded: "+record.name
        } catch(e:Exception) { issue=e.message?:"LiteRT failed to load model" }
        finally { loading=false }
    }
    suspend fun removeModel(record:ModelRecord) {
        hub.unloadForRemoval(record.role, File(record.uri).name)
        ModelImporter(context).remove(record)
        reload()
    }
    suspend fun submitSignal(source:String,text:String,priority:Int=4) {
        val id=repository.incoming(source,text,priority)
        if(id!=null)SignalWorker.schedule(context)
        reload()
    }
    suspend fun approveCommitment(trace:DecisionTraceRecord) {
        try {
            val current=dao.decision(trace.id)?:error("Decision no longer exists")
            require(current.decision=="ASK") {"Already resolved"}
            val incoming=dao.signal(trace.signalId?:error("Source signal missing"))
                ?:error("Source signal missing")
            require(incoming.status=="PROCESSED") {"Signal not safely processed"}
            val proposal=com.shiv.assistant.automation.CommitmentParser.analyze(
                incoming.fact,java.time.ZoneId.systemDefault())
            require(proposal is com.shiv.assistant.automation.CommitmentProposal.Ready) {
                "Commitment is ambiguous; provide day and time before approving."
            }
            val deadline=proposal.whenAt.toInstant().toEpochMilli()
            require(deadline>System.currentTimeMillis()) {"Event time has passed"}
            val event=EventRecord(incoming.id,proposal.title,deadline,null,incoming.source,
                proposal.confidence)
            val reminderId="event-"+incoming.id
            val reminderAt=(deadline-15*60_000L).coerceAtLeast(System.currentTimeMillis()+1000L)
            // The entire calendar/reminder decision is one encrypted transaction.
            dao.confirmEvent(event,ReminderRecord(reminderId,
                "Upcoming: "+proposal.title.take(180),reminderAt,event.id),
                current.copy(decision="APPROVED",reason="Owner reviewed and approved event; "+
                    "notification queued",policyTier="ASK_FIRST"),
                ActionLogRecord("approval-"+incoming.id,"CREATE_EVENT","[REDACTED]",
                    "ASK_FIRST","OWNER_APPROVED",true,"EVENT_AND_REMINDER_SAVED",null))
            com.shiv.assistant.automation.ShivReminders.scheduleApprovedEvent(context,reminderId,reminderAt)
            progress="Approved event and scheduled reminder"
            reload()
        } catch(e:Exception) { issue=e.message?:"Approval failed" }
    }
    suspend fun undoApprovedCommitment(trace:DecisionTraceRecord) {
        val current=dao.decision(trace.id) ?: run { issue="Decision not found";return }
        val eventId=current.signalId ?: run { issue="Source event unavailable";return }
        val audit=ActionLogRecord("undo-"+eventId,"UNDO_EVENT","[REDACTED]",
            "ASK_FIRST","OWNER_REQUESTED",true,"EVENT_REMINDER_CANCELLED",null)
        val success=dao.undoApprovedEvent(current.id,eventId,audit)
        if(success) {
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork(
                "shiv-approved-event-"+eventId)
            progress="Local approved event and pending reminder reversed; delivered alerts remain"
        } else issue="This approval has already been reversed or is no longer pending"
        reload()
    }
    suspend fun declineCommitment(trace:DecisionTraceRecord) {
        val current=dao.decision(trace.id)?:return
        if(current.decision=="ASK") dao.trace(current.copy(decision="DECLINED",
            reason="Owner declined the suggested commitment"))
        reload()
    }
    suspend fun correctMemory(id:String,replacement:String) {
        if(replacement.isBlank())return
        repository.correct(id,replacement)
        reload()
    }
    suspend fun deleteMemory(id:String) { repository.forget(id);reload() }
    suspend fun removeBelief(id:String) { repository.forgetBelief(id);reload() }
    fun modelStatus():String {
        val router=hub.router.status()
        val chat=hub.conversation.status()
        return computeLedger.report()+" | Router: "+(router.modelName?:"not loaded")+
            " | Conversation: "+(chat.modelName?:"not loaded")+
            " | Queue: "+hub.inference.pendingCount()+
            " | Load ms: "+(chat.lastLoadMs?.toString()?:"not measured")+
            " | Reply ms: "+(chat.lastReplyMs?.toString()?:"not measured")+
            " | First text fragment ms: "+(chat.firstFragmentMs?.toString()?:"not measured")+
            " | Output chars: "+(chat.lastOutputCharacters?.toString()?:"not measured")
    }
}
