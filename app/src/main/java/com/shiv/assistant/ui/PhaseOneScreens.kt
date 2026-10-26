package com.shiv.assistant.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import com.shiv.assistant.PhaseOneController
import kotlinx.coroutines.launch

/**
 * Separate operational panels: loaded-model status is not conflated with mere
 * imported metadata, and pending approvals are never shown as complete actions.
 */
@Composable
fun PhaseOneScreen(page:String, controller:PhaseOneController, onModelPick:()->Unit) {
    val scope=rememberCoroutineScope()
    val permissionCalendar=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) scope.launch { controller.readPhoneCalendar() }
    }
    val permissionPhone=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        controller.enablePostCallCheckIn(granted)
    }
    var role by remember { mutableStateOf("router") }
    var editing by remember { mutableStateOf<com.shiv.assistant.data.MemoryRecord?>(null) }
    var revised by remember { mutableStateOf("") }
    var allowedScreenPackage by remember { mutableStateOf("") }
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
                    TextButton(onClick={controller::cancelModel}){Text("CANCEL")}
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
                Text("Observed on this device, where available. TTFT, tokens/s, battery cost, NPU delegate and thermal impact are not measured yet.",
                    color=Color.LightGray)
                Text("Pending encrypted signals: "+controller.notificationCount)
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
                    scope.launch { controller.readPhoneCalendar() }
                    permissionCalendar.launch(Manifest.permission.READ_CALENDAR)
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
                    TextButton(onClick={controller::discardCalendarPreview}) {
                        Text("DISCARD ALL PREVIEWS")
                    }
                OutlinedButton(onClick={permissionPhone.launch(Manifest.permission.READ_PHONE_STATE)}) {
                    Text("OPT INTO POST-CALL CHECK-INS")
                }
                OutlinedButton(onClick={controller.setPatternLearning(true)}) {
                    Text("ENABLE CHARGING-ONLY PATTERN LEARNING")
                }
                OutlinedButton(onClick={controller.setPatternLearning(false)}) {
                    Text("DISABLE PATTERN LEARNING")
                }
                Text("BANKING / PAYMENTS / PASSWORD MANAGERS / AUTHENTICATOR apps are denied before any Shiv capture, storage or AI request.",
                    color=accent)
                Text("Shiv does not record telephone call audio or read other apps' private files.",color=Color.LightGray)
                Text("For offline Kannada/Telugu TTS: Settings > General management > Language > Text-to-speech > Preferred engine > Speech Services by Google > gear > Install voice data.",
                    color=Color.White)
            }
        }
    }
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
