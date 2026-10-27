package com.shiv.assistant.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import com.shiv.assistant.PhaseOneController
import com.shiv.assistant.ShivController
import com.shiv.assistant.domain.AuthorityTier
import com.shiv.assistant.data.PortableOwnerArchive
import com.shiv.assistant.data.OwnerNudgeReaction
import com.shiv.assistant.domain.AppSourceMode
import com.shiv.assistant.ai.LocalBackend
import kotlinx.coroutines.launch

/**
 * Separate operational panels: loaded-model status is not conflated with mere
 * imported metadata, and pending approvals are never shown as complete actions.
 */
@Composable
fun PhaseOneScreen(page:String, controller:PhaseOneController, owner:ShivController, onModelPick:()->Unit) {
    val scope=rememberCoroutineScope()
    var contactQuery by remember { mutableStateOf("") }
    var micWakePhrase by remember { mutableStateOf(false) }
    var micSleepOverride by remember { mutableStateOf(false) }
    val screenContext=LocalContext.current
    val permissionCalendar=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) scope.launch { controller.readPhoneCalendar() }
    }
    val permissionContacts=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) scope.launch { controller.lookupContactNames(contactQuery) }
    }
    val permissionMicSession=rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { granted ->
        if(granted)controller.startOfflineMicSession(owner.speechLocale,micWakePhrase,micSleepOverride)
        else owner.showNotice("Microphone access denied: offline session not started")
    }
    val permissionPhone=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        controller.enablePostCallCheckIn(granted)
    }
    var role by remember { mutableStateOf("router") }
    var editing by remember { mutableStateOf<com.shiv.assistant.data.MemoryRecord?>(null) }
    var revised by remember { mutableStateOf("") }
    var allowedScreenPackage by remember { mutableStateOf("") }
    var newDeniedPackage by remember { mutableStateOf("") }
    var sourcePackage by remember { mutableStateOf("") }
    var sourceMode by remember { mutableStateOf(AppSourceMode.NOTIFICATIONS_ONLY) }
    var confirmErase by remember { mutableStateOf(false) }
    var erasePhrase by remember { mutableStateOf("") }
    var portableDialog by remember { mutableStateOf<String?>(null) }
    var portablePassphrase by remember { mutableStateOf("") }
    var portableConfirm by remember { mutableStateOf("") }
    var portableBusy by remember { mutableStateOf(false) }
    var portableResult by remember { mutableStateOf("") }
    val archiveExportPicker=rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val secret=portablePassphrase.toCharArray()
        portablePassphrase=""
        if(uri==null) {secret.fill('\u0000');portableResult="Export canceled"}
        else scope.launch {
            portableBusy=true
            try {
                val size=PortableOwnerArchive(screenContext).exportTo(uri,secret)
                portableResult="Encrypted backup saved ("+size+" bytes). Keep its passphrase safe."
            } catch (_:Exception) {
                portableResult="Backup export failed. No personal data was sent online."
            } finally {secret.fill('\u0000');portableBusy=false}
        }
    }
    val archiveImportPicker=rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()) { uri ->
        val secret=portablePassphrase.toCharArray()
        portablePassphrase=""
        if(uri==null) {secret.fill('\u0000');portableResult="Restore canceled"}
        else scope.launch {
            portableBusy=true
            try {
                val count=PortableOwnerArchive(screenContext).restoreFrom(uri,secret)
                owner.afterPortableRestore(count)
                portableResult="Archive restored. Old reminders, permissions and cloud transmission remain off."
            } catch (_:Exception) {
                portableResult="Restore refused (wrong passphrase, unsupported archive or invalid records). Existing data remains unchanged."
            } finally {secret.fill('\u0000');portableBusy=false}
        }
    }
    val accent=Color(0xFF65F5F0)
    LaunchedEffect(page) { runCatching { controller.reload() } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text(page.uppercase(),style=MaterialTheme.typography.headlineSmall,color=accent)
        controller.issue?.let { Text(it,color=Color(0xFFFF9191)) }
        Text(controller.progress,color=Color.LightGray,fontSize=11.sp)
        when(page) {
            "Models" -> {
                Text("Choose a .litertlm file in Files. LiteRT needs an app-private copy even if the original is in Downloads. Importing E2B may require about 4 GB extra free storage; no model is bundled.",
                    color=Color.LightGray)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(role=="router",onClick={role="router"},label={Text("1B router")})
                    FilterChip(role=="conversation",onClick={role="conversation"},label={Text("E2B conversation")})
                }
                Button(onClick=onModelPick) { Text("SELECT MODEL FILE") }
                if(controller.candidate!=null) {
                    Text("Ready to copy: "+controller.candidate?.lastPathSegment, color=Color.White)
                    Button(enabled=!controller.loading,onClick={scope.launch{controller.importModel(role)}}) {
                        Text("CONFIRM APP-PRIVATE COPY")
                    }
                    TextButton(onClick=controller::cancelModel){Text("CANCEL")}
                }
                Text(controller.progress,color=Color.LightGray)
                controller.issue?.let{Text(it,color=Color(0xFFFF9191))}
                controller.models.forEach { model ->
                    Surface(tonalElevation=3.dp,shape=MaterialTheme.shapes.large) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(model.name,color=Color.White)
                            Text(model.role+" • "+(model.bytes/(1024*1024))+" MB • "+model.sha256.take(12),fontSize=11.sp)
                            Row {
                                TextButton(onClick={scope.launch{controller.loadModel(model)}}) { Text("LOAD") }
                                TextButton(onClick={scope.launch{controller.removeModel(model)}}) { Text("REMOVE") }
                            }
                        }
                    }
                }
            }
            "AI Status" -> {
                Text(controller.modelStatus(),color=Color.White)
                Text("First text fragment is measured locally. The LiteRT response does not expose an exact generated-token count, so tokens/s is unavailable. Battery cost and NPU performance need owner-device measurements.",
                    color=Color.LightGray)
                Text("LOCAL MODEL BACKEND",color=accent)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    LocalBackend.entries.forEach { backend ->
                        FilterChip(selected=controller.localBackend==backend,
                            onClick={scope.launch { controller.chooseLocalBackend(backend) }},
                            label={Text(backend.name)})
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Text("GPU/NPU only work when this phone, runtime and model support them. A failed load does not silently change backend. Reload each model after changing settings.",
                    color=Color.LightGray,fontSize=11.sp)
                Text("MAX LOCAL OUTPUT TOKENS",color=accent)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(128,256,384,512,1024).forEach { count ->
                        FilterChip(selected=controller.localOutputTokens==count,
                            onClick={scope.launch { controller.chooseLocalOutputTokens(count) }},
                            label={Text(count.toString())})
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Text("OWNER LOCAL COMPUTE ALLOWANCE",color=accent)
                Text(controller.localComputeReport(),color=Color.LightGray,fontSize=11.sp)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(60,300,600,1800).forEach { seconds ->
                        FilterChip(selected=controller.localBudgetSeconds==seconds,
                            onClick={controller.setLocalComputeBudget(seconds)},
                            label={Text((seconds/60).toString()+"m")})
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Text("The budget is measured LiteRT wall time, not battery mAh; " +
                    "native cancellation may be delayed. Owner-requested reminders do not consume it.",
                    color=Color.LightGray,fontSize=11.sp)
                Text("Pending encrypted signals: "+controller.notificationCount)
                Text("Failed signals retained for owner review: "+controller.failedSignalCount,
                    color=Color.LightGray,fontSize=11.sp)
                if(controller.failedSignalCount>0) {
                    OutlinedButton(onClick={scope.launch { controller.retryFailedSignals() }}) {
                        Text("RETRY FAILED SIGNALS")
                    }
                }
                Button(onClick={scope.launch{controller.reload()}}){Text("REFRESH")}
            }
            "Events" -> {
                Text("Events below are read from Shiv's encrypted database. Parsed commitments awaiting your decision appear in Decisions, not as confirmed calendar events.",
                    color=Color.LightGray)
                if(controller.upcoming.isEmpty())Text("No recorded events in the next 48 hours.",color=Color.LightGray)
                controller.upcoming.forEach { event ->
                    Text(event.title+" • "+java.time.Instant.ofEpochMilli(event.startAt).toString(),
                        color=Color.White)
                }
                Text("ENCRYPTED OPEN TASKS",color=accent)
                if(controller.upcomingTasks.isEmpty())
                    Text("No unfinished tasks in Shiv's private agenda.",color=Color.LightGray)
                controller.upcomingTasks.forEach { task ->
                    Text(task.title+" • "+(task.dueAt?.let {
                        java.time.Instant.ofEpochMilli(it).toString()
                    } ?: "No due date"),color=Color.White)
                }
            }
            "Beliefs" -> {
                Text("What Shiv thinks he knows about you. These are hypotheses, not confirmed facts. You can remove any entry.",
                    color=Color.LightGray)
                controller.beliefs.forEach { belief ->
                    Surface(shape=MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(12.dp)) {
                            Text(belief.hypothesis)
                            Text("Confidence "+(belief.confidence*100).toInt()+"% • "+belief.evidence,
                                style=MaterialTheme.typography.labelSmall)
                            TextButton(onClick={scope.launch{controller.removeBelief(belief.id)}}) { Text("DELETE BELIEF") }
                        }
                    }
                }
                if(controller.beliefs.isEmpty())Text("No learned patterns recorded.",color=Color.LightGray)
            }
            "Memories" -> {
                Text("Encrypted facts, with explicit corrections and deletion.",color=Color.LightGray)
                controller.memories.forEach { memory ->
                    Surface(shape=MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                            Text(memory.text,color=Color.White)
                            Text(memory.source+" • "+memory.category+
                                " • confidence "+(memory.confidence*100).toInt()+"%",fontSize=11.sp)
                            Row {
                                TextButton(onClick={editing=memory;revised=memory.text}) { Text("CORRECT") }
                                TextButton(onClick={scope.launch{controller.deleteMemory(memory.id)}}) {
                                    Text("DELETE")
                                }
                            }
                        }
                    }
                }
                if(controller.memories.isEmpty()) Text("No stored facts yet",color=Color.LightGray)
            }
            "Decisions" -> {
                Text("Evidence traces from the durable signal queue. ASK requires owner confirmation and does not create a calendar entry automatically.",
                    color=Color.LightGray)
                controller.decisions.forEach { t ->
                    Surface(shape=MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(10.dp)) {
                            Text(t.decision,color=accent)
                            Text(t.reason,color=Color.White)
                            if(t.decision=="APPROVED" && t.reason.contains("approved event",true)) {
                                TextButton(onClick={scope.launch { controller.undoApprovedCommitment(t) }}) {
                                    Text("UNDO APPROVED EVENT + REMINDER")
                                }
                            }
                            if(t.decision=="ASK") {
                                Row {
                                    if(t.reason.startsWith("Possible event:")) {
                                        TextButton(onClick={scope.launch{controller.approveCommitment(t)}}) {
                                            Text("APPROVE EVENT + REMINDER")
                                        }
                                    }
                                    TextButton(onClick={scope.launch{controller.declineCommitment(t)}}) {
                                        Text("DISMISS")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "Permissions" -> {
                Text("Notification access, Accessibility, contacts, calls, microphone and calendar each require explicit permission. Do not grant permissions unless you want the associated feature.",
                    color=Color.LightGray)
                Text("APP AWARENESS PAUSE",color=accent)
                Text("When awareness is enabled, Shiv shows a persistent notification with PAUSE or RESUME. Pausing blocks notification and screen facts before they are read. Resuming never rearms a screen preview.",
                    color=Color.LightGray,fontSize=11.sp)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick=controller::pauseAwareness) { Text("PAUSE ALL AWARENESS") }
                    OutlinedButton(onClick=controller::resumeAwareness) { Text("RESUME") }
                }
                Text("To enable optional Accessibility: Settings > Accessibility > Installed apps > Shiv. On some Galaxy phones, first open Settings > Apps > Shiv > three dots > Allow restricted settings.",
                    color=Color.White)
                OutlinedButton(onClick={controller.openAccessibilitySettings()}) { Text("OPEN ACCESSIBILITY SETTINGS") }
                OutlinedTextField(value=allowedScreenPackage,
                    onValueChange={allowedScreenPackage=it.take(150)},
                    label={Text("Allowed app package (exact)")},singleLine=true)
                Button(enabled=allowedScreenPackage.isNotBlank(),
                    onClick={controller.requestOneShotAwareness(allowedScreenPackage)}) {
                    Text("ARM ONLY THIS APP FOR 15 SECONDS")
                }
                OutlinedButton(onClick={controller.stopOneShotAwareness()}) {
                    Text("STOP SCREEN AWARENESS")
                }
                OutlinedButton(onClick={
                    if(screenContext.checkSelfPermission(Manifest.permission.READ_CALENDAR)==
                        android.content.pm.PackageManager.PERMISSION_GRANTED)
                        scope.launch { controller.readPhoneCalendar() }
                    else permissionCalendar.launch(Manifest.permission.READ_CALENDAR)
                }) { Text("PREVIEW CALENDAR — DON'T IMPORT YET") }
                controller.calendarCandidates.forEach { candidate ->
                    Surface(shape=MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                            Text(candidate.title.take(180),color=Color.White)
                            Text(java.time.Instant.ofEpochMilli(candidate.startAt).toString(),
                                color=Color.LightGray,fontSize=11.sp)
                            TextButton(onClick={scope.launch{controller.confirmCalendarImport(candidate.id)}}) {
                                Text("APPROVE IMPORT")
                            }
                        }
                    }
                }
                if(controller.calendarCandidates.isNotEmpty())
                    TextButton(onClick=controller::discardCalendarPreview) {
                        Text("DISCARD ALL PREVIEWS")
                    }
                Text("APP USAGE • OWNER-REQUESTED PREVIEW",color=accent)
                Text("Read only 24-hour foreground duration totals after manually granting " +
                    "Android Usage Access. Sensitive apps are excluded. Results are temporary, " +
                    "never entered into memory or an AI prompt.",
                    color=Color.LightGray,fontSize=11.sp)
                OutlinedButton(onClick=controller::openUsageAccessSettings) {
                    Text("OPEN USAGE ACCESS SETTINGS")
                }
                OutlinedButton(onClick={scope.launch{controller.previewUsage()}}) {
                    Text("PREVIEW APP USAGE")
                }
                controller.usagePreview.forEach { item ->
                    Text(item.packageName+" • "+(item.foregroundMs/60_000L)+" min",
                        color=Color.LightGray,fontSize=11.sp)
                }
                if(controller.usagePreview.isNotEmpty()) {
                    OutlinedButton(onClick=controller::clearUsagePreview) {
                        Text("CLEAR USAGE PREVIEW")
                    }
                }
                Text("CONTACTS — NAME SEARCH ONLY",color=accent)
                OutlinedTextField(value=contactQuery,
                    onValueChange={contactQuery=it.take(60)},
                    label={Text("Contact name to search")},singleLine=true)
                OutlinedButton(enabled=contactQuery.trim().length>=2,
                    onClick={
                        if(screenContext.checkSelfPermission(Manifest.permission.READ_CONTACTS)==
                            android.content.pm.PackageManager.PERMISSION_GRANTED)
                            scope.launch { controller.lookupContactNames(contactQuery) }
                        else permissionContacts.launch(Manifest.permission.READ_CONTACTS)
                    }) { Text("SEARCH ON DEVICE (NO NUMBERS SAVED)") }
                controller.contactNameMatches.forEach { match ->
                    Text(match,color=Color.White)
                }
                if(controller.contactNameMatches.isNotEmpty())
                    TextButton(onClick=controller::clearContactNames) { Text("CLEAR CONTACT RESULTS") }
                OutlinedButton(onClick={permissionPhone.launch(Manifest.permission.READ_PHONE_STATE)}) {
                    Text("OPT INTO POST-CALL CHECK-INS")
                }
                Text("USER-STARTED OFFLINE MIC SESSION",color=accent)
                Text("On-device recognition only; no cloud STT or autonomous wake-word listener. " +
                    "Android shows a persistent microphone notification with STOP. " +
                    "The session ends within 10 minutes or earlier if the device gets hot, " +
                    "battery falls below 25%, or offline recognition is unavailable. " +
                    "Only non-sensitive recognized commitments go to Shiv's encrypted ASK_FIRST queue.",
                    color=Color.LightGray,fontSize=11.sp)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=micWakePhrase,onCheckedChange={micWakePhrase=it})
                    Text("Require 'Shiv' before each recognized command",color=Color.LightGray,fontSize=11.sp)
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=micSleepOverride,onCheckedChange={micSleepOverride=it})
                    Text("Allow this session during 10 PM–9 AM",color=Color.LightGray,fontSize=11.sp)
                }
                Text("Proximity plus darkness pauses recognition while pocketed. The phrase check is not speaker identification.",
                    color=Color.LightGray,fontSize=11.sp)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick={
                        if(screenContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==
                            android.content.pm.PackageManager.PERMISSION_GRANTED)
                            controller.startOfflineMicSession(owner.speechLocale,micWakePhrase,micSleepOverride)
                        else permissionMicSession.launch(Manifest.permission.RECORD_AUDIO)
                    }) { Text("START OFFLINE MIC") }
                    OutlinedButton(onClick=controller::stopOfflineMicSession) { Text("STOP MIC") }
                }
                Text("OPT-IN PROACTIVE AGENDA",color=accent)
                Text("Private, approximate hourly checks of events you approved. " +
                    "Quiet hours 10 PM–9 AM; maximum three optional notices per day, " +
                    "reduced if you repeatedly dismiss/correct them. " +
                    "No background AI, microphone, cloud call or guessed commitments.",
                    color=Color.LightGray,fontSize=11.sp)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("Optional agenda nudges",Modifier.weight(1f),color=Color.White)
                    Switch(checked=controller.proactiveOptIn,
                        onCheckedChange={controller.changeProactiveOptIn(it)})
                }
                Text("Weekly self-review: "+controller.proactiveFeedback,
                    color=Color.LightGray,fontSize=11.sp)
                if(controller.proactiveLastKey.isNotBlank()) {
                    Text("Review your last optional notification (owner feedback only):",
                        color=Color.LightGray,fontSize=11.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        OwnerNudgeReaction.entries.forEach { reaction ->
                            TextButton(onClick={controller.recordProactiveReaction(reaction)}) {
                                Text(reaction.name,fontSize=10.sp)
                            }
                        }
                    }
                }
                OutlinedButton(onClick=controller::refreshProactiveFeedback) {
                    Text("REFRESH OPTIONAL-NOTICE FEEDBACK")
                }
                Text("OWNER REMINDER AUTHORITY",color=accent)
                Row {
                    FilterChip(owner.reminderTier==AuthorityTier.ASK_FIRST,
                        onClick={scope.launch{owner.changeReminderTier(AuthorityTier.ASK_FIRST)}},
                        label={Text("Ask first")})
                    Spacer(Modifier.width(8.dp))
                    FilterChip(owner.reminderTier==AuthorityTier.ACT_THEN_TELL,
                        onClick={scope.launch{owner.changeReminderTier(AuthorityTier.ACT_THEN_TELL)}},
                        label={Text("Schedule, then tell")})
                }
                Text("Only explicit local reminders may use act-then-tell. Payments and messages still require review.",
                    color=Color.LightGray,fontSize=11.sp)
                OutlinedButton(onClick={scope.launch{owner.undoLatestReminder()}}) {
                    Text("UNDO LATEST PENDING REMINDER")
                }
                OutlinedButton(onClick={controller.setPatternLearning(true)}) {
                    Text("ENABLE CHARGING-ONLY PATTERN LEARNING")
                }
                OutlinedButton(onClick={controller.setPatternLearning(false)}) {
                    Text("DISABLE PATTERN LEARNING")
                }
                Text("OWNER APP SOURCE EXCLUSIONS",color=accent)
                Text("Block additional apps before Shiv reads notification or screen details. " +
                    "Built-in banking/OTP exclusions cannot be removed.",
                    color=Color.LightGray,fontSize=11.sp)
                OutlinedTextField(value=newDeniedPackage,
                    onValueChange={newDeniedPackage=it.take(160)},
                    label={Text("Android package name to deny")},singleLine=true)
                Button(enabled=newDeniedPackage.isNotBlank(),onClick={
                    val app=newDeniedPackage
                    scope.launch { runCatching { controller.addDeniedPackage(app) } }
                    newDeniedPackage=""
                }) { Text("BLOCK THIS APP") }
                controller.deniedPackages.forEach { denied ->
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(denied,Modifier.weight(1f),color=Color.LightGray,fontSize=11.sp)
                        TextButton(onClick={scope.launch { controller.removeDeniedPackage(denied) }}) {
                            Text("UNBLOCK")
                        }
                    }
                }
                Text("PER-APP SOURCE CONTROL",color=accent)
                Text("Include allows opt-in notification facts and a separately armed 15-second screen preview. Notifications only blocks screen reading. Exclude blocks both. Android grants and the built-in sensitive-app denylist always take priority.",
                    color=Color.LightGray,fontSize=11.sp)
                OutlinedTextField(value=sourcePackage,
                    onValueChange={sourcePackage=it.take(160)},
                    label={Text("Android package to configure")},singleLine=true)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    AppSourceMode.entries.forEach { mode ->
                        FilterChip(selected=sourceMode==mode,
                            onClick={sourceMode=mode},
                            label={Text(mode.name.replace('_',' '))})
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Button(enabled=sourcePackage.isNotBlank(),onClick={
                    val name=sourcePackage
                    scope.launch {
                        runCatching { controller.setAppSourceMode(name,sourceMode) }
                            .onFailure { owner.showNotice(it.message ?: "App policy could not be saved") }
                    }
                    sourcePackage=""
                }) { Text("SAVE APP POLICY") }
                controller.appSourceModes.toSortedMap().forEach { (app,mode) ->
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("$app • ${mode.name.replace('_',' ')}",
                            Modifier.weight(1f),color=Color.LightGray,fontSize=11.sp)
                        TextButton(onClick={scope.launch { controller.removeAppSourceMode(app) }}) {
                            Text("RESET")
                        }
                    }
                }
                Text("BANKING / PAYMENTS / PASSWORD MANAGERS / AUTHENTICATOR apps are denied before any Shiv capture, storage or AI request.",
                    color=accent)
                Text("Shiv does not record telephone call audio or read other apps' private files.",color=Color.LightGray)
                HorizontalDivider()
                Text("PORTABLE ENCRYPTED BACKUP / RESTORE",color=accent)
                Text("Owner-selected file, 12–256 character passphrase, AES-256-GCM. " +
                    "No internet and no plaintext backup files. Imported model weights are excluded. " +
                    "Restore replaces stored owner records and leaves imported reminders inactive. " +
                    "Store your passphrase separately: Shiv cannot recover it.",
                    color=Color.LightGray,fontSize=11.sp)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled=!portableBusy,onClick={
                        portableDialog="export";portableResult=""
                    }) { Text("EXPORT ENCRYPTED") }
                    OutlinedButton(enabled=!portableBusy,onClick={
                        portableDialog="restore";portableResult=""
                    }) { Text("RESTORE ARCHIVE") }
                }
                if(portableResult.isNotBlank())
                    Text(portableResult,color=Color.LightGray,fontSize=11.sp)
                HorizontalDivider()
                Text("OWNER DATA ERASURE",color=Color(0xFFFF9191))
                Text("Permanently removes private Shiv records and cloud credentials. " +
                    "Retains imported .litertlm model files. This cannot be undone.",
                    color=Color.LightGray,fontSize=11.sp)
                OutlinedButton(onClick={confirmErase=true}) { Text("ERASE OWNER DATA…") }
                Text("For offline Kannada/Telugu TTS: Settings > General management > Language > Text-to-speech > Preferred engine > Speech Services by Google > gear > Install voice data.",
                    color=Color.White)
            }
        }
    }
    if(portableDialog!=null) AlertDialog(
        onDismissRequest={
            portableDialog=null
            portablePassphrase=""
            portableConfirm=""
        },
        title={ Text(if(portableDialog=="export") "Encrypted owner backup" else "Restore encrypted owner data") },
        text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(if(portableDialog=="export")
                    "Choose a 12+ character passphrase. You will save an authenticated, portable backup. No model binaries are exported."
                else "WARNING: replaces Shiv personal records from the chosen archive. Prior pending reminders and approvals will not execute. Cloud consent is disabled after restore.")
                OutlinedTextField(value=portablePassphrase,
                    onValueChange={portablePassphrase=it.take(256)},singleLine=true,
                    visualTransformation=PasswordVisualTransformation(),
                    label={Text("Archive passphrase")})
                if(portableDialog=="export")
                    OutlinedTextField(value=portableConfirm,
                        onValueChange={portableConfirm=it.take(256)},singleLine=true,
                        visualTransformation=PasswordVisualTransformation(),
                        label={Text("Confirm passphrase")})
                else
                    OutlinedTextField(value=portableConfirm,
                        onValueChange={portableConfirm=it.take(20)},singleLine=true,
                        label={Text("Type RESTORE SHIV")})
            }
        },
        confirmButton={
            val canProceed=portablePassphrase.length in 12..256 &&
                (if(portableDialog=="export")portablePassphrase==portableConfirm
                 else portableConfirm=="RESTORE SHIV")
            Button(enabled=canProceed && !portableBusy,onClick={
                val mode=portableDialog
                portableDialog=null
                portableConfirm=""
                if(mode=="export")archiveExportPicker.launch("shiv-owner-backup.shiv")
                else archiveImportPicker.launch(arrayOf("application/octet-stream","*/*"))
            }) { Text("CHOOSE "+if(portableDialog=="export")"BACKUP DESTINATION" else "ARCHIVE FILE") }
        },
        dismissButton={TextButton(onClick={
            portableDialog=null;portablePassphrase="";portableConfirm=""
        }) { Text("CANCEL") }}
    )
    if(confirmErase) AlertDialog(
        onDismissRequest={confirmErase=false;erasePhrase=""},
        title={Text("Permanently erase personal Shiv data?")},
        text={
            Column {
                Text("Notes, chat, events, reminders, rules, learned beliefs, " +
                    "approval logs, API secrets and permissions will be cleared. " +
                    "Imported public model weights are kept. Enter ERASE SHIV DATA to continue.")
                OutlinedTextField(erasePhrase,{erasePhrase=it.take(15)},singleLine=true,
                    label={Text("Type ERASE SHIV DATA")})
            }
        },
        confirmButton={
            Button(enabled=erasePhrase=="ERASE SHIV DATA",onClick={
                confirmErase=false
                erasePhrase=""
                scope.launch { runCatching { owner.eraseOwnerRecords() } }
            }) { Text("ERASE PERSONAL RECORDS") }
        },
        dismissButton={TextButton(onClick={confirmErase=false;erasePhrase=""}) {
            Text("CANCEL")
        }}
    )
    if(editing!=null) AlertDialog(
        onDismissRequest={editing=null},
        title={Text("Correct memory")},
        text={OutlinedTextField(value=revised,onValueChange={revised=it.take(8192)},
            minLines=2,label={Text("Corrected fact")})},
        confirmButton={
            Button(enabled=revised.isNotBlank(),onClick={
                val record=editing
                editing=null
                if(record!=null) scope.launch{controller.correctMemory(record.id,revised)}
            }) {Text("SAVE CORRECTION")}
        },
        dismissButton={TextButton(onClick={editing=null}){Text("CANCEL")}}
    )
}
