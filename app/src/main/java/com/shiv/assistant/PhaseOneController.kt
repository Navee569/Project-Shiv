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
import com.shiv.assistant.integrations.WritableCalendar
import com.shiv.assistant.integrations.CalendarExportPolicy
import com.shiv.assistant.integrations.ContactsAdapter
import com.shiv.assistant.domain.SensitiveAppDenylist
import com.shiv.assistant.domain.SourceExclusions
import com.shiv.assistant.domain.AppSourceAccess
import com.shiv.assistant.domain.AppSourceMode
import com.shiv.assistant.domain.DueDateProposal
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import com.shiv.assistant.awareness.ScreenInspectionPolicy
import com.shiv.assistant.awareness.AwarenessControl
import androidx.compose.runtime.*
import com.shiv.assistant.ai.*
import com.shiv.assistant.automation.SignalWorker
import com.shiv.assistant.automation.ShivAgendaNudgeWorker
import com.shiv.assistant.data.OwnerNudgeLedger
import com.shiv.assistant.data.OwnerNudgeReaction
import com.shiv.assistant.data.LocalModelComputeLedger
import com.shiv.assistant.data.DailyEnergyLedger
import com.shiv.assistant.integrations.UsageOverview
import com.shiv.assistant.domain.UsageObservation
import com.shiv.assistant.data.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Live developer panels are honest about imported, loaded, and missing models. */
class PhaseOneController(private val context: Context, val hub: ShivBrainHub) {
    private val repository=ShivRepository(context)
    private val data=PhaseOneDataRepository(context)
    private val reads=PhaseOneReadRepository(context)
    private val decisionsData=CommitmentDecisionRepository(context)
    private val calendarExportMutex=Mutex()
    val models=mutableStateListOf<ModelRecord>()
    val memories=mutableStateListOf<MemoryRecord>()
    val upcoming=mutableStateListOf<EventRecord>()
    val upcomingTasks=mutableStateListOf<TaskRecord>()
    val beliefs=mutableStateListOf<BeliefRecord>()
    val decisions=mutableStateListOf<DecisionTraceRecord>()
    val calendarCandidates=mutableStateListOf<CalendarCandidate>()
    val writableCalendars=mutableStateListOf<WritableCalendar>()
    val contactNameMatches=mutableStateListOf<String>()
    val deniedPackages=mutableStateListOf<String>()
    val appSourceModes=mutableStateMapOf<String,AppSourceMode>()
    val usagePreview=mutableStateListOf<UsageObservation>()
    private val nudgeLedger=OwnerNudgeLedger(ShivVault(context))
    private val computeLedger=LocalModelComputeLedger(ShivVault(context))
    private val energyLedger=DailyEnergyLedger(context)
    var localEnergyBudgetMWh by mutableIntStateOf(energyLedger.budgetMWh())
        private set
    var localBudgetSeconds by mutableIntStateOf(computeLedger.budgetSeconds())
        private set
    var localBackend by mutableStateOf(data.localBackend())
        private set
    var localOutputTokens by mutableIntStateOf(data.localOutputTokens())
        private set
    suspend fun chooseLocalBackend(value:LocalBackend) {
        hub.clearSessions()
        data.setLocalBackend(value)
        localBackend=value
        progress="Backend saved; models unloaded. Press LOAD for each model. Unsupported delegates report a load error."
    }
    suspend fun chooseLocalOutputTokens(value:Int) {
        require(value in 64..1024)
        hub.clearSessions()
        data.setLocalOutputTokens(value)
        localOutputTokens=value
        progress="Output limit saved; models unloaded. Press LOAD for each model."
    }
    var proactiveOptIn by mutableStateOf(data.proactiveOptIn())
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
    var refreshing by mutableStateOf(false)
        private set
    var issue by mutableStateOf<String?>(null)
        private set
    fun reportIssue(message:String) { issue=message }
    fun clearIssue() { issue=null }
    var notificationCount by mutableIntStateOf(0)
        private set
    var failedSignalCount by mutableIntStateOf(0)
        private set
    fun openAccessibilitySettings() {
        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    fun requestOneShotAwareness(packageName:String) {
        if(!AwarenessControl.canShow(context)) {
            issue="Allow Shiv notifications to show its persistent awareness pause control first"
            return
        }
        val allowed=packageName.trim().lowercase()
        if(!ScreenInspectionPolicy.permits(allowed, allowed)) {
            issue="Enter one non-sensitive, fully qualified Android package"
            return
        }
        // The exact allowed source is persisted encrypted; a broad Accessibility
        // grant does not authorize Shiv to traverse other apps.
        data.setScreenAllowedPackage(allowed)
        check(context.getSharedPreferences("shiv_awareness_control",Context.MODE_PRIVATE).edit()
            .putBoolean("owner_enabled",true).putBoolean("paused",false)
            .putLong("capture_until",System.currentTimeMillis()+15_000L).commit())
        AwarenessControl.show(context)
        issue=null
        progress="One-shot capture armed for 15 seconds, ONLY for "+allowed
    }
    fun stopOneShotAwareness() {
        AwarenessControl.pause(context)
        data.setScreenAllowedPackage("")
        progress="One-shot screen inspection stopped"
    }
    fun pauseAwareness() { AwarenessControl.pause(context);progress="All app awareness paused" }
    fun resumeAwareness() { AwarenessControl.resume(context);progress="Notification awareness resumed; screen needs a new 15-second arm" }
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
        data.importCalendar(approved)
        calendarCandidates.remove(candidate)
        progress="Owner-approved calendar event saved in encrypted Shiv storage"
        reload()
    }
    fun discardCalendarPreview() { calendarCandidates.clear();progress="Calendar preview discarded" }
    suspend fun loadWritableCalendars() {
        writableCalendars.clear()
        try {
            writableCalendars.addAll(CalendarAdapter(context).writableCalendars())
            issue=null
            progress=if(writableCalendars.isEmpty()) "No writable calendar found or permission missing"
                else "Choose an event and calendar, then review the export"
        } catch (_:Exception) { issue="Could not read writable calendars" }
    }
    suspend fun exportEvent(reviewed:EventRecord,calendarId:Long) = calendarExportMutex.withLock {
        try {
            val event=data.currentEvent(reviewed.id) ?: error("Event no longer exists")
            require(event==reviewed) { "Event changed after review; refresh before exporting" }
            require(CalendarExportPolicy.approved(event,System.currentTimeMillis())) {
                "Event is expired, sensitive, or already imported from a calendar"
            }
            require(!data.exportedCalendarEvent(event.id)) { "This event was already exported" }
            // Keep the provider write and duplicate guard serialized for this controller.
            val externalId=CalendarAdapter(context).writeApprovedEvent(event,calendarId)
            data.markCalendarExport(event.id,calendarId,externalId)
            if(!data.logCalendarExport()) {
                issue="Phone calendar event was created, but Shiv could not record the audit entry"
                progress="External calendar event ID $externalId created; inspect the calendar"
                return@withLock
            }
            issue=null
            progress="Event created in your selected phone calendar (ID $externalId)"
        } catch(e:Exception) { issue=e.message ?: "Calendar export failed; inspect phone calendar before retrying" }
    }
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
        val revoked=data.addDeniedPackage(packageName)
        // Any currently armed window for this app is revoked immediately.
        if(revoked) {
            context.getSharedPreferences("shiv_awareness_control",Context.MODE_PRIVATE)
                .edit().remove("capture_until").putBoolean("paused",true).commit()
        }
        reload()
        progress="App source excluded before notification or screen capture"
    }
    suspend fun removeDeniedPackage(packageName:String) = withContext(Dispatchers.IO) {
        data.removeDeniedPackage(packageName)
        reload()
        progress="Custom exclusion removed; built-in banking denylist remains enforced"
    }
    suspend fun setAppSourceMode(packageName:String,mode:AppSourceMode) = withContext(Dispatchers.IO) {
        data.setAppSourceMode(packageName,mode)
        reload()
        progress="Source policy saved; Android permissions and one-shot screen review still apply"
    }
    suspend fun removeAppSourceMode(packageName:String) = withContext(Dispatchers.IO) {
        data.removeAppSourceMode(packageName)
        reload()
        progress="App returned to default policy; built-in and owner denylists still apply"
    }
    fun startOfflineMicSession(locale:String,requireWake:Boolean=false,allowSleep:Boolean=false,
        continuous:Boolean=false) {
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
                .putExtra(ShivMicSessionService.EXTRA_LOCALE,locale)
                .putExtra(ShivMicSessionService.EXTRA_REQUIRE_WAKE,requireWake)
                .putExtra(ShivMicSessionService.EXTRA_ALLOW_SLEEP,allowSleep)
                .putExtra(ShivMicSessionService.EXTRA_CONTINUOUS,continuous))
            issue=null
            progress=if(continuous)"Owner-started offline microphone active until STOP or a safety gate ends it."
                else "Owner-started offline microphone session requested. Notification STOP available; expires within 10 minutes."
        } catch (_:Exception) {
            issue="Android blocked the microphone session. Start it with Shiv in foreground."
        }
    }
    fun stopOfflineMicSession() {
        context.stopService(Intent(context,ShivMicSessionService::class.java))
        progress="Offline microphone session stopped"
    }
    fun openUsageAccessSettings() {
        context.startActivity(android.content.Intent(
            android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    suspend fun previewUsage() {
        usagePreview.clear()
        val reader=UsageOverview(context)
        if(!reader.hasOwnerGrant()) {
            issue="Enable Shiv manually under Android Special app access → Usage access"
            return
        }
        try {
            usagePreview.addAll(reader.lastDay())
            progress="Owner-requested 24h app usage preview; nothing was saved or sent to AI"
        } catch (_:Exception) { issue="Unable to read Android app usage metadata" }
    }
    fun clearUsagePreview() { usagePreview.clear() }
    fun setLocalComputeBudget(seconds:Int) {
        require(seconds in 60..3600)
        computeLedger.setBudgetSeconds(seconds)
        localBudgetSeconds=seconds
        progress="Local model daily compute allowance: "+(seconds/60)+" min. " +
            "This is not a measured battery budget."
    }
    fun localComputeReport():String=computeLedger.report()
    fun localEnergyReport():String=energyLedger.report()
    fun setLocalEnergyBudget(value:Int) {
        energyLedger.setBudgetMWh(value)
        localEnergyBudgetMWh=value
        progress="Whole-device observation-window cap saved; it may include other apps"
    }
    fun changeProactiveOptIn(enabled:Boolean) {
        data.setProactiveOptIn(enabled)
        proactiveOptIn=enabled
        ShivAgendaNudgeWorker.updateRegistration(context,enabled)
        com.shiv.assistant.automation.ProactiveTriggerWorker.updateRegistration(context,enabled)
        progress=if(enabled) "Owner-enabled, quiet-hour-limited optional agenda notices"
            else "Optional agenda notices disabled"
    }
    fun recordProactiveReaction(reaction:OwnerNudgeReaction) {
        if(nudgeLedger.lastKey().isBlank()) {
            issue="No optional agenda notification exists to review"
            return
        }
        nudgeLedger.react(reaction)
        if(reaction==OwnerNudgeReaction.SNOOZED)
            ShivAgendaNudgeWorker.scheduleSnooze(context)
        proactiveLastKey=nudgeLedger.lastKey()
        proactiveFeedback=nudgeLedger.report()
        progress="Feedback saved encrypted. Only optional future interruption caps may decrease."
    }
    fun refreshProactiveFeedback() {
        proactiveLastKey=nudgeLedger.lastKey()
        proactiveFeedback=nudgeLedger.report()
    }
    fun refreshPrivacyAfterOwnerReset() {
        proactiveOptIn=data.proactiveOptIn()
        refreshProactiveFeedback()
        clearUsagePreview()
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
        data.saveReviewedTask(title,dueAt)
        progress="Owner-confirmed offline OCR task saved to encrypted Shiv agenda"
        reload()
    }
    suspend fun reload() {
        withContext(Dispatchers.Main) { refreshing=true }
        try {
        val snapshot=reads.snapshot()
        withContext(Dispatchers.Main) {
            models.clear();models.addAll(snapshot.models)
            memories.clear();memories.addAll(snapshot.memories)
            upcoming.clear();upcoming.addAll(snapshot.events)
            upcomingTasks.clear();upcomingTasks.addAll(snapshot.tasks)
            beliefs.clear();beliefs.addAll(snapshot.beliefs)
            decisions.clear();decisions.addAll(snapshot.decisions)
            notificationCount=snapshot.pendingSignals
            failedSignalCount=snapshot.failedSignals
            deniedPackages.clear();deniedPackages.addAll(snapshot.deniedPackages)
            appSourceModes.clear();appSourceModes.putAll(snapshot.appSourceModes)
        }
        } finally { withContext(NonCancellable+Dispatchers.Main) { refreshing=false } }
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
            hub.load(record.role, File(record.uri), lease.freeRamBytes,record.id)
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
    suspend fun retryFailedSignals() {
        val retried=withContext(Dispatchers.IO) { data.retryFailedSignals() }
        if(retried>0)SignalWorker.schedule(context)
        progress="$retried preserved signal(s) returned to the encrypted queue"
        reload()
    }
    suspend fun approveCommitment(trace:DecisionTraceRecord) {
        try {
            val reminder=decisionsData.approve(trace.id,trace.signalId)
            com.shiv.assistant.automation.ShivReminders.scheduleApprovedEvent(
                context,reminder.id,reminder.dueAt)
            progress="Approved event and scheduled reminder"
            reload()
        } catch(e:Exception) { issue=e.message?:"Approval failed" }
    }
    suspend fun undoApprovedCommitment(trace:DecisionTraceRecord) {
        val eventId=runCatching { decisionsData.undo(trace.id) }
            .getOrElse { issue=it.message?:"Decision unavailable";return }
        if(eventId!=null) {
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork(
                "shiv-approved-event-"+eventId)
            progress="Local approved event and pending reminder reversed; delivered alerts remain"
        } else issue="This approval has already been reversed or is no longer pending"
        reload()
    }
    suspend fun declineCommitment(trace:DecisionTraceRecord) {
        decisionsData.decline(trace.id)
        reload()
    }
    suspend fun correctMemory(id:String,replacement:String) {
        if(replacement.isBlank())return
        repository.correct(id,replacement)
        reload()
    }
    suspend fun deleteMemory(id:String) { repository.forget(id);reload() }
    suspend fun removeBelief(id:String) { repository.forgetBelief(id);reload() }
    suspend fun correctBelief(id:String,text:String) {
        repository.correctBelief(id,text)
        reload()
        progress="Belief corrected as an explicit owner fact"
    }
    fun modelStatus():String {
        val router=hub.router.status()
        val chat=hub.conversation.status()
        val measured=if(hub.lastRoutedRole=="router")router else chat
        return computeLedger.report()+" | "+energyLedger.report()+
            " | Router: "+(router.modelName?:"not loaded")+
            " | Conversation: "+(chat.modelName?:"not loaded")+
            " | Queue: "+hub.inference.pendingCount()+
            " | Last measured role: "+(hub.lastRoutedRole?:"none")+
            " | Load ms: "+(measured.lastLoadMs?.toString()?:"not measured")+
            " | Reply ms: "+(measured.lastReplyMs?.toString()?:"not measured")+
            " | First text fragment ms: "+(measured.firstFragmentMs?.toString()?:"not measured")+
            " | Runtime TTFT ms: "+(measured.runtimeTtftMs?.toString()?:"unavailable")+
            " | Decode tokens: "+(measured.decodeTokens?.toString()?:"unavailable")+
            " | Decode tokens/s: "+(measured.decodeTokensPerSecond?.let { String.format(java.util.Locale.ROOT,"%.1f",it) }?:"unavailable")+
            " | Whole-device energy drop during reply mWh: "+
                (measured.deviceEnergyDropNwh?.let { String.format(java.util.Locale.ROOT,"%.3f",it/1_000_000.0) }?:"unavailable")+
            " | "+com.shiv.assistant.voice.SpeechDiagnostics.report()+
            " | Output chars: "+(measured.lastOutputCharacters?.toString()?:"not measured")+
            " | Backend: "+(measured.backend?:"not loaded")+
            " | Max output tokens: "+(measured.maxOutputTokens?.toString()?:"not loaded")+
            " | Last route: "+(hub.lastRoutedRole?:"none")+
            " in "+(hub.lastRoutingMs?.toString()?:"not measured")+" ms"+
            " | Fallback: "+(hub.lastFallbackReason?:"none")
    }
}
