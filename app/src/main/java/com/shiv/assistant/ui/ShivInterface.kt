package com.shiv.assistant.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.shiv.assistant.ShivController
import com.shiv.assistant.domain.*
import com.shiv.assistant.data.AgendaKind
import com.shiv.assistant.data.OwnerReaction
import com.shiv.assistant.data.AgendaDates
import kotlinx.coroutines.launch

private val Neon = Color(0xFF65F5F0)
private val Violet = Color(0xFFA693FF)
private val Muted = Color(0xFF97AAC4)
private val Frame = Color(0xFF24364D)
private val Panel = Color(0xFF101C2C)

@Composable
fun ShivInterface(
    controller: ShivController,
    onVoice: () -> Unit,
    onPhoto: () -> Unit,
    onOfflineOcr: () -> Unit,
    onDocument: () -> Unit,
    onModel: () -> Unit,
    onUnlockMemory: () -> Unit,
    onApproveHighRisk: () -> Unit,
    onBackupExport: (String) -> Unit,
    onBackupRestore: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onPostCallPermission: () -> Unit,
    onCalendarPreview: () -> Unit,
    onContactLookup: (String) -> Unit,
    onNotificationPermission: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("Core") }
    var draft by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Capability?>(null) }
    var editingFactId by remember { mutableStateOf<String?>(null) }
    var correctionText by remember { mutableStateOf("") }
    var contactQuery by remember { mutableStateOf("") }
    var screenPackage by remember { mutableStateOf("") }
    val pending = controller.pending
    val neuralTool = controller.pendingNeuralTool
    val photo = controller.visionCandidate
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF050A14), Color(0xFF0C1930), Color(0xFF090A17))))
            .safeDrawingPadding()
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("S H I V", color = Neon, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp)
                    Text("PERSONAL INTELLIGENCE SYSTEM", fontSize = 9.sp, letterSpacing = 2.sp, color = Muted)
                }
                Text("●  " + controller.config.mode.name, color = Neon, fontSize = 10.sp,
                    modifier = Modifier.border(1.dp, Frame, RoundedCornerShape(24.dp)).padding(10.dp))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf("Core", "Chat", "Tasks", "Automate", "Memory", "Models", "Status", "Privacy", "Grants", "Settings").forEach {
                    val active = tab == it
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (active) Neon else Panel,
                        modifier = Modifier.clickable { tab = it }
                    ) {
                        Text(it.uppercase(), color = if (active) Color(0xFF051521) else Muted,
                            fontWeight = FontWeight.Bold, fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp))
                    }
                }
            }
        }
        HorizontalDivider(color = Frame)
        when (tab) {
            "Core" -> LazyColumn(
                contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp),
                modifier = Modifier.weight(1f)
            ) {
                item { ShivOrb() }
                item { Text("INTELLIGENCE MODULES", color = Neon, fontSize = 12.sp, letterSpacing = 2.sp) }
                item {
                    Text("Every module is tracked. Built-in features work locally; connected features require real provider authorization.",
                        color = Muted, fontSize = 12.sp)
                }
                items(ShivCapabilities.all.groupBy { it.group }.toList()) { (group, modules) ->
                    GlassCard {
                        Text(group.uppercase(), color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Spacer(Modifier.height(8.dp))
                        modules.forEach { capability ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { selected = capability }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(capability.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text(capability.description, color = Muted, fontSize = 10.sp, maxLines = 2)
                                }
                                Text(
                                    when (capability.state) {
                                        CapabilityState.BUILT_IN -> "ACTIVE"
                                        CapabilityState.REQUIRES_CONNECTION -> "CONNECT"
                                        CapabilityState.REQUIRES_DEVICE_PERMISSION -> "PERMISSION"
                                        CapabilityState.PLANNED -> "PENDING"
                                    }, fontSize = 9.sp, color = if (capability.state == CapabilityState.BUILT_IN) Neon else Violet
                                )
                            }
                        }
                    }
                }
            }
            "Chat" -> if (controller.guestMode) Column(Modifier.weight(1f).padding(18.dp)) {
                Header("GUEST MODE", "Chat history is hidden. Disable guest mode in Privacy.")
            } else Column(Modifier.weight(1f)) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(controller.messages) { entry ->
                        val mine = entry.role == "user"
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                            Surface(color = if (mine) Color(0xFF1C4150) else Panel,
                                shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth(0.92f)) {
                                Text(entry.text, color = Color(0xFFE9F5FF), fontSize = 14.sp,
                                    modifier = Modifier.padding(15.dp))
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(draft, { draft = it.take(4000) }, modifier = Modifier.weight(1f),
                        label = { Text("Ask Shiv or request an action") },
                        maxLines = 3, shape = RoundedCornerShape(17.dp))
                    Button(
                        onClick = {
                            val text = draft; draft = ""
                            scope.launch { controller.submit(text) }
                        },
                        enabled = !controller.working && draft.isNotBlank(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp)
                    ) { Text("SEND") }
                }
                if (controller.ocrPreview.isNotBlank()) {
                    GlassCard {
                        Text("LOCAL OCR PREVIEW — NOT SAVED", color = Neon, fontSize = 11.sp)
                        Text(controller.ocrPreview, color = Color.White, fontSize = 12.sp)
                        Text("Latin script only. Kannada and Telugu scripts are not supported by this bundled OCR model.",
                            color = Muted, fontSize = 11.sp)
                        TextButton(onClick = controller::clearOcrPreview) { Text("CLEAR PREVIEW") }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onVoice) { Text("🎙 Voice", color = Neon) }
                    TextButton(onClick = onPhoto) { Text("◈ Cloud Vision", color = Neon) }
                    TextButton(onClick = onOfflineOcr) { Text("◈ Offline OCR", color = Neon) }
                    TextButton(onClick = { controller.messages.lastOrNull { it.role == "assistant" }?.let { onSpeak(it.text) } }) {
                        Text("◉ Speak", color = Neon)
                    }
                }
            }
            "Tasks" -> if (controller.guestMode)
                Column(Modifier.weight(1f).padding(18.dp)) {
                    Header("PRIVATE AGENDA LOCKED", "Disable Guest Mode in Privacy to see scheduled items.")
                } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Header("EVENTS & TASKS", "Owner-entered dates. No events are invented or created from uncertain speech.")
                var itemTitle by remember { mutableStateOf("") }
                var itemDate by remember { mutableStateOf("") }
                var itemType by remember { mutableStateOf(AgendaKind.TASK) }
                OutlinedTextField(itemTitle, { itemTitle = it.take(300) },
                    label = { Text("Event or task name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(itemDate, { itemDate = it.take(16) },
                    label = { Text("Date and time — YYYY-MM-DD HH:mm") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = itemType == AgendaKind.TASK, onClick = { itemType = AgendaKind.TASK })
                    Text("Task", color = Color.White)
                    RadioButton(selected = itemType == AgendaKind.EVENT, onClick = { itemType = AgendaKind.EVENT })
                    Text("Event", color = Color.White)
                }
                Button(onClick = { controller.createAgendaItem(itemTitle, itemDate, itemType) },
                    enabled = itemTitle.isNotBlank() && itemDate.isNotBlank()) { Text("SAVE TO PRIVATE AGENDA") }
                Header("NEXT 48 HOURS", "Only saved records, never model guesses.")
                controller.agenda.upcoming().forEach { item ->
                    GlassCard {
                        Text(item.title, color = Color.White)
                        Text(AgendaDates.formatLocal(item.dueEpochMs), color = Violet, fontSize = 12.sp)
                    }
                }
                Header("ALL SAVED ITEMS", "Stored encrypted in SQLCipher.")
                controller.agendaItems.forEach { item ->
                    GlassCard {
                        Text(item.kind.name + " • " + item.title, color = Color.White)
                        Text(AgendaDates.formatLocal(item.dueEpochMs), color = Muted, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = item.completed,
                                onCheckedChange = { controller.completeAgendaItem(item.id, it) })
                            Text("Complete", color = Muted, modifier = Modifier.weight(1f), fontSize = 11.sp)
                            TextButton(onClick = { controller.removeAgendaItem(item.id) }) { Text("DELETE") }
                        }
                    }
                }
            }
            "Automate" -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp)) {
                Header("OWNER-CONTROLLED PROACTIVE ASSISTANT",
                    "Optional deterministic agenda notices; no microphone, background AI or cloud calls.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Scheduled agenda nudges & briefs", Modifier.weight(1f),
                        color = Color.White, fontSize = 12.sp)
                    Switch(checked = controller.proactiveOptIn,
                        onCheckedChange = controller::updateProactiveOptIn)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Learn hypotheses from completed events", Modifier.weight(1f),
                        color = Color.White, fontSize = 12.sp)
                    Switch(checked = controller.passiveLearningOptIn,
                        onCheckedChange = controller::updatePassiveLearningOptIn)
                }
                Text("Quiet hours 10 PM–8 AM, daily notification cap 3. Repeated completions " +
                    "create unconfirmed beliefs only; review or delete them under Memory.",
                    color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(16.dp))
                Header("POST-CALL CHECK-IN",
                    "Optional generic Android call-ended prompt; no caller details or audio.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ask whether a call produced a commitment", Modifier.weight(1f),
                        color = Color.White, fontSize = 12.sp)
                    Switch(checked = controller.postCallOptIn, onCheckedChange = {
                        if (it) onPostCallPermission() else controller.updatePostCallOptIn(false)
                    })
                }
                Text("Android may delay or suppress background call-state broadcasts. " +
                    "Shiv never records the other caller's audio.",
                    color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(12.dp))
                Header("ANDROID CALENDAR • OWNER PREVIEW",
                    "Read next 14 days only when requested. Review the temporary list before encrypted import.")
                Button(onClick = onCalendarPreview, enabled = !controller.guestMode &&
                    !controller.sensingPaused && !controller.privacyZone) { Text("PREVIEW DEVICE CALENDAR") }
                controller.calendarPreview.forEach { entry ->
                    Text(entry.title + " • " + AgendaDates.formatLocal(entry.startMillis),
                        color = Color.White, fontSize = 11.sp)
                }
                if (controller.calendarPreview.isNotEmpty()) {
                    Row {
                        Button(onClick = controller::importCalendarPreview) { Text("IMPORT PREVIEWED EVENTS") }
                        TextButton(onClick = controller::clearCalendarPreview) { Text("DISCARD") }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Header("CONTACTS • ON-DEMAND NAME LOOKUP",
                    "Only owner-search matching names. Numbers aren't copied or saved.")
                OutlinedTextField(contactQuery, { contactQuery = it.take(60) },
                    label = { Text("Contact name") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { onContactLookup(contactQuery) },
                    enabled = contactQuery.trim().length >= 2 && !controller.guestMode &&
                        !controller.sensingPaused && !controller.privacyZone) { Text("LOOK UP CONTACT") }
                controller.contactMatches.forEach { match ->
                    Text(match, color = Color.White, fontSize = 12.sp)
                }
                if (controller.contactMatches.isNotEmpty())
                    TextButton(onClick = controller::clearContactPreview) { Text("CLEAR RESULTS") }
                Spacer(Modifier.height(16.dp))
                Header("APP USAGE • ON-DEMAND",
                    "Only device-approved aggregate usage, with sensitive apps removed before display.")
                OutlinedButton(onClick = controller::openUsageAccessSettings) {
                    Text("OPEN USAGE ACCESS SETTINGS")
                }
                Button(onClick = controller::previewUsage, enabled = !controller.guestMode &&
                    !controller.sensingPaused && !controller.privacyZone) {
                    Text("PREVIEW LAST 24H APP USAGE")
                }
                controller.usagePreview.forEach { item ->
                    Text(item.packageName + " • " + item.durationMs / 60000 + " min",
                        color = Color.White, fontSize = 11.sp)
                }
                if (controller.usagePreview.isNotEmpty())
                    TextButton(onClick = controller::clearUsagePreview) { Text("CLEAR USAGE PREVIEW") }
                Spacer(Modifier.height(16.dp))
                Header("PLAN & EXECUTION", "Safe automation: review each step before an external action.")
                Spacer(Modifier.height(15.dp))
                OutlinedTextField(goal, { goal = it.take(1000) }, label = { Text("Your goal / macro") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { controller.saveMacro(goal) }, enabled = goal.isNotBlank()) { Text("SAVE MACRO") }
                    OutlinedButton(onClick = { if (goal.isNotBlank()) controller.propose(ShivPlanner.plan(goal).steps.first()) },
                        enabled = goal.isNotBlank()) { Text("REVIEW FIRST STEP") }
                }
                Spacer(Modifier.height(16.dp))
                Text("PARSED STEPS", color = Violet, fontSize = 12.sp)
                ShivPlanner.plan(goal).steps.forEachIndexed { index, step ->
                    GlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("%02d".format(index + 1), color = Neon, fontSize = 22.sp, modifier = Modifier.width(48.dp))
                            Column(Modifier.weight(1f)) {
                                Text(step.kind.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text(step.argument.take(120), color = Muted, fontSize = 12.sp)
                            }
                            TextButton(onClick = { controller.propose(step) }) { Text("REVIEW", color = Neon) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(12.dp))
                Header("YOUR MACROS", "Stored encrypted. No unattended app control.")
                controller.macros.forEach { saved ->
                    GlassCard {
                        Text(saved, color = Color.White)
                        Row {
                            TextButton(onClick = { goal = saved }) { Text("LOAD", color = Neon) }
                            TextButton(onClick = { controller.propose(ShivPlanner.plan(saved).steps.first()) }) {
                                Text("REVIEW", color = Violet)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text("Reminders: type “remind me in 10 minutes to take a break” in Chat.",
                    color = Muted, fontSize = 12.sp)
            }
            "Memory" -> if (controller.guestMode || !controller.privateUnlocked)
                Column(Modifier.weight(1f).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Header("PRIVATE MEMORY LOCKED", "Biometric/device credential authentication required.")
                    if (!controller.guestMode) Button(onClick = onUnlockMemory) { Text("UNLOCK PRIVATE MEMORY") }
                    else Text("Turn off Guest Mode in Privacy first.", color = Muted)
                } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp)) {
                Header("PRIVATE MEMORY", "All local records are encrypted with Android Keystore AES-GCM.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(note, { note = it.take(8192) }, label = { Text("Save an explicit memory") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { controller.saveNote(note); note = "" }, enabled = note.isNotBlank()) { Text("SAVE") }
                    OutlinedButton(onClick = onDocument) { Text("IMPORT TEXT") }
                }
                Spacer(Modifier.height(20.dp))
                Text("SAVED NOTES", color = Violet, fontSize = 12.sp)
                Text("CORRECTABLE FACTS & EVIDENCE", color = Violet, fontSize = 12.sp)
                controller.knowledge.active().takeLast(20).asReversed().forEach { fact ->
                    GlassCard {
                        Text(fact.text, color = Color.White)
                        Text("Source: " + fact.source + " • Confidence: " + fact.confidence,
                            color = Muted, fontSize = 11.sp)
                        Row {
                            TextButton(onClick = { editingFactId = fact.id; correctionText = fact.text }) {
                                Text("CORRECT")
                            }
                            TextButton(onClick = { controller.deleteStructuredMemory(fact.id) }) {
                                Text("DELETE FACT")
                            }
                        }
                    }
                }
                Header("ENCRYPTED BACKUP", "Export or merge-restore personal records through Android Files. Model weights are excluded.")
                var backupPhrase by remember { mutableStateOf("") }
                OutlinedTextField(backupPhrase, { backupPhrase = it.take(128) },
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("12+ character backup passphrase") },
                    modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onBackupExport(backupPhrase) },
                        enabled = backupPhrase.length >= 12 && !controller.working) { Text("EXPORT") }
                    OutlinedButton(onClick = { onBackupRestore(backupPhrase) },
                        enabled = backupPhrase.length >= 12 && !controller.working) { Text("RESTORE") }
                }
                Text("Losing this passphrase makes the backup unrecoverable. Restore merges or overwrites matching entries only.",
                    color = Muted, fontSize = 11.sp)
                Text("LEGACY SAVED NOTES", color = Violet, fontSize = 12.sp)
                controller.notes.asReversed().take(30).forEach { memory ->
                    GlassCard { Text(memory, color = Color.White, fontSize = 13.sp) }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text("Notification summaries: " + runCatching { controller.vault.notificationCount() }.getOrDefault(0),
                    color = Muted)
                OutlinedButton(onClick = { controller.clearHistory() }) { Text("ERASE CHAT HISTORY") }
            }
            "Models" -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Header("LOCAL MODEL MANAGER", "Import files without copying multi-GB weights. Runtime validation and inference are not available yet.")
                Button(onClick = onModel) { Text("SELECT MODEL FILE") }
                controller.models.forEach { model ->
                    GlassCard {
                        Text(model.name, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(model.status + " • " + (model.sizeBytes / 1_000_000) + " MB",
                            color = Muted, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { controller.assignModelRole(model.id, false) },
                                enabled = !controller.working && model.sizeBytes <= 2_000_000_000L) {
                                Text(if (controller.routerModelId == model.id) "ROUTER ✓" else "SET ROUTER",
                                    fontSize = 11.sp)
                            }
                            OutlinedButton(onClick = { controller.assignModelRole(model.id, true) },
                                enabled = !controller.working) {
                                Text(if (controller.conversationModelId == model.id) "CHAT ✓" else "SET CHAT",
                                    fontSize = 11.sp)
                            }
                        }
                        Button(onClick = { scope.launch { controller.loadModel(model.id) } },
                            enabled = !controller.working) { Text("LOAD OFFLINE MODEL") }
                        TextButton(onClick = { controller.removeModel(model.id) },
                            enabled = !controller.working) { Text("REMOVE REFERENCE") }
                    }
                }
                Text("Roles load on demand: ROUTER is a model up to 2 GB; CHAT is the larger conversation model. " +
                    "Only one local LiteRT engine stays loaded at a time. Large files may be copied to private storage.",
                    color = Muted, fontSize = 12.sp)
                Text("Active model: " + (controller.activeModelId?.let { id ->
                    controller.models.firstOrNull { it.id == id }?.name
                } ?: "NONE"), color = Neon)
                Text("Local neural model loaded: " + controller.neuralReady, color = Neon)
                Button(onClick = { scope.launch { controller.unloadModel() } }, enabled = controller.neuralReady) { Text("UNLOAD MODEL") }
                Text("Android runtime validates model compatibility at load; import does not mean it is running.",
                    color = Muted, fontSize = 12.sp)
            }
            "Status" -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Header("LIVE DEVICE DIAGNOSTICS", "Readings originate on this device, never from CI benchmarks.")
                Button(onClick = controller::refreshDeviceStats) { Text("MEASURE NOW") }
                val stats = controller.telemetry
                GlassCard {
                    Text("Battery: " + (stats?.batteryPercent?.toString()?.plus("%") ?: "NOT MEASURED"), color = Color.White)
                    Text("Charging: " + (stats?.charging?.toString() ?: "NOT MEASURED"), color = Color.White)
                    Text("Thermal state: " + (stats?.thermalStatus?.toString() ?: "NOT MEASURED"), color = Color.White)
                    Text("Free RAM: " + (stats?.availableRamMb?.toString()?.plus(" MB") ?: "NOT MEASURED"), color = Color.White)
                    Text("Queued signals: " + runCatching { controller.signalQueue.pendingCount() }.getOrDefault(0), color = Color.White)
                    Text("Queued local inference: " + controller.localQueueLength, color = Color.White)
                    Text("Large model: " + (controller.leaseForConversation()?.allowLargeModel?.toString()
                        ?: "NOT EVALUATED"), color = Muted)
                }
                Header("RESOURCE POLICY", "Heavy work is limited by power, temperature, RAM and user budget.")
                Text("Daily local-model compute limit: " + controller.dailyComputeBudgetSeconds / 60 +
                    " minutes • measured use: " + controller.energy.modelSecondsToday() / 60 +
                    " whole minutes", color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(60, 300, 600, 1800).forEach { seconds ->
                        OutlinedButton(onClick = { controller.changeDailyComputeBudgetSeconds(seconds) },
                            modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp)) {
                            Text((seconds / 60).toString() + "m", fontSize = 11.sp)
                        }
                    }
                }
                Text("Last local admission: " + controller.lastResourceDecision,
                    color = Muted, fontSize = 12.sp)
                PowerMode.entries.forEach { mode ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = controller.powerMode == mode, onClick = { controller.changePowerMode(mode) })
                        Text(mode.name, color = Color.White)
                    }
                }
                Header("MEASURED LOCAL INFERENCE", "First chunk is not necessarily the first token; no token counts are estimated.")
                val inference = controller.localMetrics
                Text("First response chunk: " + (inference?.firstChunkLatencyMs?.toString()?.plus(" ms") ?: "NOT MEASURED"),
                    color = Muted, fontSize = 12.sp)
                Text("Total response: " + (inference?.totalLatencyMs?.toString()?.plus(" ms") ?: "NOT MEASURED"),
                    color = Muted, fontSize = 12.sp)
                Text("Output characters: " + (inference?.outputCharacters?.toString() ?: "NOT MEASURED"),
                    color = Muted, fontSize = 12.sp)
                Header("WEEKLY SELF-REVIEW", "Counts use owner-entered reactions, not guessed task outcomes.")
                Text(controller.feedback.weeklyReport(), color = Muted, fontSize = 11.sp)
                Header("ENERGY LEDGER", "Only recorded activity is shown; no invented battery usage numbers.")
                Text(runCatching { controller.energy.report() }.getOrDefault("No measurements"), color = Muted, fontSize = 11.sp)
            }
            "Privacy" -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Header("PRIVACY & SENSING KILL SWITCH", "Controls optional notification and future sensing, without disabling offline reminders.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("PAUSE SENSING", Modifier.weight(1f), color = Color.White)
                    Switch(controller.sensingPaused, onCheckedChange = controller::updateSensingPaused)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("GUEST MODE", Modifier.weight(1f), color = Color.White)
                    Switch(controller.guestMode, onCheckedChange = controller::updateGuestMode)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("PRIVACY ZONE (MANUAL)", Modifier.weight(1f), color = Color.White)
                    Switch(controller.privacyZone, onCheckedChange = controller::updatePrivacyZone)
                }
                var denied by remember { mutableStateOf("") }
                OutlinedTextField(denied, { denied = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Add an excluded app package") })
                Button(onClick = {
                    runCatching { controller.denyPackage(denied) }
                    denied = ""
                }, enabled = denied.isNotBlank()) { Text("EXCLUDE APP") }
                Text("Additional excluded apps: " + controller.extraDeniedPackages().ifBlank { "None" },
                    color = Muted, fontSize = 11.sp)
                Header("ON-DEMAND ACCESSIBILITY",
                    "Enable manually in Android settings. Each inspection lasts at most 15 seconds.")
                OutlinedTextField(screenPackage, { screenPackage = it.take(150) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Explicitly allowed screen-app package") })
                OutlinedButton(onClick = {
                    runCatching { controller.allowScreenPackage(screenPackage) }
                    screenPackage = ""
                }, enabled = screenPackage.isNotBlank()) { Text("ALLOW ONE APP") }
                Text("Allowed packages: " + controller.screenAllowedPackages().ifBlank { "NONE" },
                    color = Muted, fontSize = 11.sp)
                OutlinedButton(onClick = controller::openAccessibilitySettings) {
                    Text("OPEN ACCESSIBILITY SETTINGS")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = controller::armScreenInspection,
                        enabled = !controller.sensingPaused && !controller.guestMode &&
                            !controller.privacyZone) { Text("ARM SCREEN LOOK • 15S") }
                    OutlinedButton(onClick = controller::disarmScreenInspection) { Text("STOP") }
                }
                Text("Only extracted planning facts from allowed apps may be stored; " +
                    "banking/OTP apps, password fields and sensitive text are rejected. " +
                    "Android may block secure windows entirely.",
                    color = Muted, fontSize = 11.sp)
                Header("DECISION AUDIT LOG", "Why did Shiv perform or reject an action?")
                if (!controller.guestMode && controller.privateUnlocked) controller.audit.recent().take(15).forEach { trace ->
                    GlassCard {
                        Text(trace.action + " • " + (if (trace.accepted) "ACCEPTED" else "DENIED"),
                            color = Color.White, fontWeight = FontWeight.Bold)
                        Text(trace.reason, color = Muted, fontSize = 12.sp)
                        Text("Authority: " + trace.tier, color = Violet, fontSize = 11.sp)
                        if (trace.action == "PROACTIVE") {
                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                OwnerReaction.entries.forEach { reaction ->
                                    TextButton(onClick = {
                                        controller.reactToTrace(trace.id, reaction)
                                    }) { Text(reaction.name, fontSize = 10.sp) }
                                }
                            }
                        }
                    }
                }
            }
            "Grants" -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Header("PERMISSIONS & REVOCATION", "Read-only device status. Shiv cannot secretly grant restricted access.")
                Button(onClick = controller::refreshPermissions) { Text("REFRESH PERMISSIONS") }
                OutlinedButton(onClick = controller::openAppPermissions) { Text("OPEN SHIV APP PERMISSIONS") }
                controller.permissionReadings.forEach { item ->
                    GlassCard {
                        Text(item.title, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(item.status, color = Violet, fontSize = 11.sp)
                        Text(item.revokeHelp, color = Muted, fontSize = 11.sp)
                    }
                }
                Text("Samsung One UI menus may differ by version. No ADB grants, root, or Shizuku integration is enabled.",
                    color = Muted, fontSize = 11.sp)
            }
            "Settings" -> if (controller.guestMode)
                Column(Modifier.weight(1f).padding(18.dp)) {
                    Header("SETTINGS LOCKED", "Disable guest mode in Privacy before changing credentials.")
                } else ShivSettings(controller, onNotificationPermission)
        }
        Text(controller.notice, color = Muted, fontSize = 10.sp, maxLines = 2,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp))
    }
    if (pending != null) AlertDialog(
        onDismissRequest = controller::reject,
        title = { Text("REVIEW ACTION • " + pending.kind.name) },
        text = { Text("Target: " + pending.argument + "\n\n" + pending.explanation +
            "\n\nShiv will open the relevant Android app. Sending or paying remains your decision.") },
        confirmButton = { Button(onClick = {
            if (pending.kind == ActionKind.UPI) onApproveHighRisk() else controller.approve()
        }) { Text("CONTINUE") } },
        dismissButton = { TextButton(onClick = controller::reject) { Text("CANCEL") } }
    )
    if (neuralTool != null) AlertDialog(
        onDismissRequest = controller::rejectNeuralTool,
        title = { Text("LOCAL MODEL ACTION • ASK FIRST") },
        text = {
            Text("Proposed tool: " + neuralTool.tool + "\n\n" +
                neuralTool.args.entries.joinToString("\n") { it.key + ": " + it.value } +
                "\n\nOnly the deterministic app policy may execute this after your approval.")
        },
        confirmButton = { Button(onClick = controller::approveNeuralTool) { Text("APPROVE") } },
        dismissButton = { TextButton(onClick = controller::rejectNeuralTool) { Text("REJECT") } }
    )
    if (photo != null) AlertDialog(
        onDismissRequest = controller::cancelImage,
        title = { Text("Share selected image with AI?") },
        text = { Text("This sends the selected image to your configured cloud endpoint. Only proceed if you trust that provider. Images stay local otherwise.") },
        confirmButton = {
            Button(onClick = { scope.launch { controller.approveVision() } },
                enabled = CloudSafety.maySend(controller.config)) { Text("SEND IMAGE") }
        },
        dismissButton = { TextButton(onClick = controller::cancelImage) { Text("KEEP PRIVATE") } }
    )
    if (editingFactId != null) AlertDialog(
        onDismissRequest = { editingFactId = null },
        title = { Text("Correct Shiv's memory") },
        text = {
            OutlinedTextField(correctionText, { correctionText = it.take(2048) },
                label = { Text("Corrected fact") }, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            Button(onClick = {
                editingFactId?.let { controller.correctStructuredMemory(it, correctionText) }
                editingFactId = null
            }, enabled = correctionText.isNotBlank()) { Text("SAVE CORRECTION") }
        },
        dismissButton = { TextButton(onClick = { editingFactId = null }) { Text("CANCEL") } }
    )
    if (selected != null) AlertDialog(
        onDismissRequest = { selected = null },
        title = { Text(selected!!.name) },
        text = { Text(selected!!.description + "\n\nAvailability: " + selected!!.state.name.replace('_', ' ')) },
        confirmButton = { TextButton(onClick = { selected = null }) { Text("GOT IT") } }
    )
}

@Composable private fun Header(title: String, description: String) {
    Text(title, color = Neon, fontSize = 15.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
    Text(description, color = Muted, fontSize = 12.sp)
}

@Composable private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Panel.copy(alpha = 0.92f), shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, Frame, RoundedCornerShape(18.dp))
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}
@Composable private fun ShivOrb() {
    Box(Modifier.fillMaxWidth().height(226.dp).clip(RoundedCornerShape(24.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF101F38), Color(0xFF0C1231)))),
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(205.dp)) {
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            drawCircle(brush = Brush.radialGradient(listOf(Neon.copy(alpha = 0.28f), Violet.copy(alpha = 0.08f), Color.Transparent)), radius = size.minDimension / 2f)
            drawCircle(color = Neon.copy(alpha = 0.55f), radius = size.minDimension * 0.39f,
                center = center, style = Stroke(2.dp.toPx()))
            drawCircle(color = Violet.copy(alpha = 0.7f), radius = size.minDimension * 0.29f,
                center = center, style = Stroke(2.dp.toPx()))
            drawCircle(color = Neon.copy(alpha = 0.7f), radius = size.minDimension * 0.16f, center = center)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("S", fontSize = 48.sp, color = Color(0xFF061626), fontWeight = FontWeight.Black)
            Text("SHIV CORE", fontSize = 9.sp, color = Color.White, letterSpacing = 2.sp)
        }
    }
}
@Composable private fun ShivSettings(controller: ShivController, askNotifications: () -> Unit) {
    var mode by remember { mutableStateOf(controller.config.mode) }
    var endpoint by remember { mutableStateOf(controller.config.endpoint) }
    var model by remember { mutableStateOf(controller.config.model) }
    var secret by remember { mutableStateOf(controller.apiKey) }
    var consent by remember { mutableStateOf(controller.config.allowCloudTransmission) }
    var error by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Header("INTELLIGENCE ROUTING", "Choose private local actions, cloud reasoning, or a hybrid that prefers local actions.")
        GlassCard {
            IntelligenceMode.entries.forEach { option ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                    .fillMaxWidth().clickable { mode = option }.padding(vertical = 5.dp)) {
                    RadioButton(selected = mode == option, onClick = { mode = option })
                    Column {
                        Text(option.name, color = Color.White)
                        Text(when (option) {
                            IntelligenceMode.LOCAL -> "Deterministic, fully offline. No cloud requests."
                            IntelligenceMode.CLOUD -> "Send questions to your configured API with consent."
                            IntelligenceMode.HYBRID -> "Keep device commands local; use cloud for other questions."
                        }, fontSize = 11.sp, color = Muted)
                    }
                }
            }
        }
        OutlinedTextField(endpoint, { endpoint = it.take(512) },
            label = { Text("OpenAI-compatible chat completions URL") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(model, { model = it.take(128) }, label = { Text("Model identifier") },
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(secret, { secret = it.take(4096) }, label = { Text("API key (encrypted)") },
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Row(Modifier.clickable { consent = !consent }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = consent, onCheckedChange = { consent = it })
            Text("I consent to sending my prompts to this provider", fontSize = 12.sp, color = Color.White)
        }
        Button(onClick = {
            runCatching { controller.settings(mode, endpoint, model, secret, consent) }
                .onSuccess { error = "" }
                .onFailure { error = it.message ?: "Invalid configuration" }
        }, modifier = Modifier.fillMaxWidth()) { Text("SAVE INTELLIGENCE SETTINGS") }
        if (error.isNotBlank()) Text(error, color = Color(0xFFFF9B9B))
        Spacer(Modifier.height(4.dp))
        Header("REMINDER AUTHORITY & UNDO", "Only the owner changes these tiers; external actions and payments always require confirmation.")
        listOf(AuthorityTier.ASK_FIRST, AuthorityTier.ACT_THEN_TELL).forEach { tier ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = controller.reminderTier == tier,
                    onClick = { controller.updateReminderTier(tier) })
                Text(if (tier == AuthorityTier.ASK_FIRST) "Ask before scheduling reminders"
                    else "Schedule explicit reminders, then tell me", color = Color.White)
            }
        }
        if (controller.maySuggestReminderAutonomy()) {
            Text("You've approved several reminders. You may choose ACT_THEN_TELL above; Shiv never changes your authority automatically.",
                color = Violet, fontSize = 11.sp)
        }
        OutlinedButton(onClick = controller::undoLatestReminder) { Text("UNDO LATEST REMINDER") }
        Text("Undo requests WorkManager cancellation. It cannot recall a notification that has already appeared.",
            color = Muted, fontSize = 11.sp)
        Header("OFFLINE VOICE LANGUAGES", "Kannada, Telugu and English. Availability depends on language data installed on the phone.")
        listOf("en-IN", "kn-IN", "te-IN").forEach { tag ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = controller.speechLocale == tag,
                    onClick = { controller.changeSpeechLocale(tag) })
                Text(tag, color = Color.White)
            }
        }
        Text("For missing voices: Galaxy Settings > General management > Language > Text-to-speech > " +
            "Preferred engine > Speech Services by Google > Settings > Install voice data. " +
            "For offline recognition, install the matching offline speech language pack. " +
            "Do not assume Telugu/Kannada is offline until verified on the Galaxy S23.",
            color = Muted, fontSize = 11.sp)
        Header("NOTIFICATION INTELLIGENCE", "Off by default. Stores only app identifiers and notification titles, never bodies.")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Save notification summaries", modifier = Modifier.weight(1f), color = Color.White)
            Switch(controller.notificationSummaries, onCheckedChange = controller::notifications)
        }
        OutlinedButton(onClick = controller::openNotificationSettings) { Text("OPEN NOTIFICATION ACCESS SETTINGS") }
        OutlinedButton(onClick = askNotifications) { Text("ALLOW REMINDER NOTIFICATIONS") }
        Spacer(Modifier.height(8.dp))
        Text("Shiv does not use Accessibility automation, install a background wake word, or silently send messages or money. Unconnected feature areas remain visible in the capability matrix.",
            color = Muted, fontSize = 12.sp)
    }
}
