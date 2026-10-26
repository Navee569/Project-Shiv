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
    onDocument: () -> Unit,
    onModel: () -> Unit,
    onSpeak: (String) -> Unit,
    onNotificationPermission: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("Core") }
    var draft by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Capability?>(null) }
    val pending = controller.pending
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
                listOf("Core", "Chat", "Automate", "Memory", "Models", "Status", "Privacy", "Settings").forEach {
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
            "Chat" -> Column(Modifier.weight(1f)) {
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
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onVoice) { Text("🎙 Voice", color = Neon) }
                    TextButton(onClick = onPhoto) { Text("◈ Vision", color = Neon) }
                    TextButton(onClick = { controller.messages.lastOrNull { it.role == "assistant" }?.let { onSpeak(it.text) } }) {
                        Text("◉ Speak", color = Neon)
                    }
                }
            }
            "Automate" -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp)) {
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
            "Memory" -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp)) {
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
                        TextButton(onClick = { controller.deleteStructuredMemory(fact.id) }) { Text("DELETE FACT") }
                    }
                }
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
                        Button(onClick = { scope.launch { controller.loadModel(model.id) } },
                            enabled = !controller.working) { Text("LOAD OFFLINE MODEL") }
                        TextButton(onClick = { controller.removeModel(model.id) }) { Text("REMOVE REFERENCE") }
                    }
                }
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
                    Text("Large model: " + (controller.leaseForConversation()?.allowLargeModel?.toString()
                        ?: "NOT EVALUATED"), color = Muted)
                }
                Header("RESOURCE POLICY", "Heavy work is limited by power, temperature, RAM and user budget.")
                PowerMode.entries.forEach { mode ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = controller.powerMode == mode, onClick = { controller.changePowerMode(mode) })
                        Text(mode.name, color = Color.White)
                    }
                }
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
                Header("DECISION AUDIT LOG", "Why did Shiv perform or reject an action?")
                controller.audit.recent().take(15).forEach { trace ->
                    GlassCard {
                        Text(trace.action + " • " + (if (trace.accepted) "ACCEPTED" else "DENIED"),
                            color = Color.White, fontWeight = FontWeight.Bold)
                        Text(trace.reason, color = Muted, fontSize = 12.sp)
                        Text("Authority: " + trace.tier, color = Violet, fontSize = 11.sp)
                    }
                }
            }
            "Settings" -> ShivSettings(controller, onNotificationPermission)
        }
        Text(controller.notice, color = Muted, fontSize = 10.sp, maxLines = 2,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp))
    }
    if (pending != null) AlertDialog(
        onDismissRequest = controller::reject,
        title = { Text("REVIEW ACTION • " + pending.kind.name) },
        text = { Text("Target: " + pending.argument + "\n\n" + pending.explanation +
            "\n\nShiv will open the relevant Android app. Sending or paying remains your decision.") },
        confirmButton = { Button(onClick = controller::approve) { Text("CONTINUE") } },
        dismissButton = { TextButton(onClick = controller::reject) { Text("CANCEL") } }
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
