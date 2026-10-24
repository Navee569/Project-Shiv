package app.shiv.ui

import app.shiv.*
import app.shiv.platform.*
import app.shiv.domain.actions.ActionTier

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import app.shiv.domain.speech.SpeechRecognitionSession
import app.shiv.domain.speech.SpeechSynthesis

internal class ShivShellState(
    val destination: MutableState<ShivDestination>,
    val chatDraft: MutableState<String>,
    val profileNameDraft: MutableState<String>,
    val profileNotesDraft: MutableState<String>,
    val profileDraftInitialized: MutableState<Boolean>,
    val profileNameEdited: MutableState<Boolean>,
    val profileNotesEdited: MutableState<Boolean>
) {
    fun editProfileName(value: String) { profileNameEdited.value = true; profileNameDraft.value = value }
    fun editProfileNotes(value: String) { profileNotesEdited.value = true; profileNotesDraft.value = value }
    fun hydrateProfile(value: UserProfile?) {
        if (value == null || profileDraftInitialized.value) return
        if (!profileNameEdited.value) profileNameDraft.value = value.name
        if (!profileNotesEdited.value) profileNotesDraft.value = value.notes
        profileDraftInitialized.value = true
    }
}

@Composable
internal fun rememberShivShellState(): ShivShellState {
    val destination = rememberShivDestination()
    val chatDraft = rememberSaveable { mutableStateOf("") }
    val profileNameDraft = rememberSaveable { mutableStateOf("") }
    val profileNotesDraft = rememberSaveable { mutableStateOf("") }
    val profileDraftInitialized = rememberSaveable { mutableStateOf(false) }
    val profileNameEdited = rememberSaveable { mutableStateOf(false) }
    val profileNotesEdited = rememberSaveable { mutableStateOf(false) }
    return remember(destination, chatDraft, profileNameDraft, profileNotesDraft, profileDraftInitialized, profileNameEdited, profileNotesEdited) {
        ShivShellState(destination, chatDraft, profileNameDraft, profileNotesDraft, profileDraftInitialized, profileNameEdited, profileNotesEdited)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShivApp(vm: ShivViewModel = viewModel()) {
    val context = LocalContext.current
    val shellState = rememberShivShellState()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val pending by vm.pendingAction.collectAsStateWithLifecycle()
    val speakReplies = remember { mutableStateOf(context.getSharedPreferences("shiv_preferences", Context.MODE_PRIVATE).getBoolean("speak_replies", true)) }
    val speaker: SpeechSynthesis = remember { OfflineSpeechOutput(context) }
    var lastSpokenId by remember { mutableStateOf<String?>(null) }
    DisposableEffect(speaker) { onDispose { speaker.close() } }
    LaunchedEffect(profile?.updatedAt) {
        shellState.hydrateProfile(profile)
    }
    LaunchedEffect(messages.lastOrNull()?.id, speakReplies.value) {
        val latest = messages.lastOrNull()
        if (latest != null && !latest.fromUser && latest.id != lastSpokenId) {
            lastSpokenId = latest.id
            if (speakReplies.value) speaker.speak(latest.text)
        }
    }

    ShivShell(state = shellState) { destination, drafts ->
        when (destination) {
            ShivDestination.CHAT -> ChatTab(
                vm, messages, drafts.chatDraft.value, { drafts.chatDraft.value = it }, Modifier.fillMaxSize()
            )
            ShivDestination.EVENTS -> EventsTab(vm, Modifier.fillMaxSize())
            ShivDestination.MEMORY -> MemoryTab(
                vm,
                drafts.profileNameDraft.value,
                drafts::editProfileName,
                drafts.profileNotesDraft.value,
                drafts::editProfileNotes,
                Modifier.fillMaxSize()
            )
            ShivDestination.SETTINGS -> SettingsTab(vm, speakReplies.value, { value ->
                speakReplies.value = value
                context.getSharedPreferences("shiv_preferences", Context.MODE_PRIVATE).edit().putBoolean("speak_replies", value).apply()
            }, onOpenDestination = { shellState.destination.value = it }, modifier = Modifier.fillMaxSize())
            ShivDestination.MODELS -> ModelManagementTab(vm, Modifier.fillMaxSize())
            ShivDestination.AI_STATUS -> AiStatusTab(vm, Modifier.fillMaxSize())
            ShivDestination.PERMISSIONS -> PermissionExplanationTab(Modifier.fillMaxSize())
        }
    }

    pending?.let { action ->
        AlertDialog(
            onDismissRequest = vm::cancelPending,
            title = { Text(action.title) },
            text = { Text(action.explanation) },
            confirmButton = {
                Button(onClick = {
                    vm.confirmPending { sms ->
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("smsto:")
                            putExtra("sms_body", sms.body)
                        }
                        runCatching { context.startActivity(intent) }
                            .getOrElse { throw it }
                    }
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = vm::cancelPending) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShivShell(
    state: ShivShellState = rememberShivShellState(),
    content: @Composable (ShivDestination, ShivShellState) -> Unit
) {
    BackHandler(enabled = !state.destination.value.isPrimary) {
        state.destination.value = ShivDestination.SETTINGS
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Shiv")
                    Text(
                        if (state.destination.value.isPrimary) "Your personal manager" else state.destination.value.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } }
            )
        },
        bottomBar = {
            NavigationBar {
                ShivDestination.entries.filter { it.isPrimary }.forEach { tab ->
                    val icon = when (tab) {
                        ShivDestination.CHAT -> Icons.Default.Chat
                        ShivDestination.EVENTS -> Icons.Default.Event
                        ShivDestination.MEMORY -> Icons.Default.Bookmark
                        else -> Icons.Default.Settings
                    }
                    NavigationBarItem(
                        selected = state.destination.value == tab,
                        onClick = { state.destination.value = tab },
                        icon = { Icon(icon, contentDescription = tab.title) },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            content(state.destination.value, state)
        }
    }
}

@Composable
private fun ChatTab(
    vm: ShivViewModel,
    messages: List<ChatMessage>,
    draft: String,
    onDraftChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val replying by vm.isReplying.collectAsStateWithLifecycle()
    val chatError by vm.chatError.collectAsStateWithLifecycle()
    var showCamera by remember { mutableStateOf(false) }
    var micNotice by remember { mutableStateOf<String?>(null) }
    var photoNotice by remember { mutableStateOf<String?>(null) }
    var speechInput by remember { mutableStateOf<SpeechRecognitionSession?>(null) }

    fun startMic() {
        val session = SpeechInput(context, onText = { transcript ->
            onDraftChange(transcript)
            micNotice = "I heard you. Check the words, then tap Send."
            speechInput = null
        }, onError = {
            micNotice = "On-device speech stopped. Check that your phone has an offline speech model installed."
            speechInput = null
        })
        if (!session.isAvailable) {
            session.close()
            micNotice = "Offline speech recognition isn’t available yet. Install an offline speech model in your Samsung speech settings."
        } else {
            speechInput = session
            micNotice = "Listening once on this phone…"
            runCatching { session.start() }.onFailure { micNotice = "Couldn’t start the microphone. Check the permission and try again." }
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startMic() else micNotice = "Microphone access was not granted."
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showCamera = true else photoNotice = "Camera access was not granted."
    }
    DisposableEffect(speechInput) { onDispose { speechInput?.close() } }

    Column(modifier) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (messages.isEmpty()) item { ChatEmptyPrompt() }
            items(messages, key = { it.id }) { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
                    Surface(
                        color = if (message.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(if (message.fromUser) .88f else .96f)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(message.text)
                            if (!message.fromUser && message.tier != null) {
                                Spacer(Modifier.height(7.dp))
                                TierChip(message.tier)
                            }
                        }
                    }
                }
            }
            item { photoNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            item { micNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            item { chatError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) } }
            if (replying) item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Shiv is working on this on your phone…")
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (granted) showCamera = true else cameraPermission.launch(Manifest.permission.CAMERA)
            }) { Icon(Icons.Default.PhotoCamera, "Take a photo") }
            IconButton(onClick = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted) startMic() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }) { Icon(Icons.Default.Mic, "Speak to Shiv") }
            OutlinedTextField(value = draft, onValueChange = onDraftChange, modifier = Modifier.weight(1f), placeholder = { Text("Message Shiv…") }, shape = RoundedCornerShape(24.dp), maxLines = 4)
            IconButton(onClick = { vm.send(draft); onDraftChange("") }, enabled = draft.isNotBlank() && !replying) { Icon(Icons.Default.Send, "Send") }
        }
    }
    if (showCamera) Dialog(onDismissRequest = { showCamera = false }) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            PhotoCapture(onClose = { showCamera = false }, onDescription = { description -> photoNotice = description; vm.appendAssistant(description) })
        }
    }
}

@Composable
internal fun ChatEmptyPrompt() {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text("Hey, I’m Shiv.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("Your loyal, slightly nosy manager. Tell me about a meeting, a promise, or something you’d like me to remember. Your data stays on this phone.")
        }
    }
}

@Composable
private fun EventsTab(vm: ShivViewModel, modifier: Modifier = Modifier) {
    val events by vm.events.collectAsStateWithLifecycle()
    val pendingEvents by vm.pendingEvents.collectAsStateWithLifecycle()
    val lead by vm.reminderLeadMinutes.collectAsStateWithLifecycle()
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Needs your review", style = MaterialTheme.typography.headlineSmall) }
        if (pendingEvents.isEmpty()) item { Text("No detected events are waiting for your confirmation.") }
        items(pendingEvents, key = { "pending-${it.id}" }) { candidate ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(candidate.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(android.text.format.DateFormat.format("EEE, MMM d · h:mm a", candidate.startsAt).toString())
                    Text("Detected while listening. Nothing is saved until you confirm.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.confirmPendingEvent(candidate) }) { Text("Save event · ASK_FIRST") }
                        TextButton(onClick = { vm.ignorePendingEvent(candidate) }) { Text("Ignore") }
                    }
                }
            }
        }
        item { Text("Upcoming", style = MaterialTheme.typography.titleLarge) }
        item {
            Text("Shiv reminds you $lead minutes early. Tell him if you want to change that.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (events.isEmpty()) item { Text("No upcoming events yet. Tell Shiv, ‘Meeting with Raj Thursday at 5.’") }
        items(events, key = { it.id }) { event ->
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(android.text.format.DateFormat.format("EEE, MMM d · h:mm a", event.startsAt).toString())
                    event.person?.let { Text("With $it") }
                    event.place?.let { Text("At $it") }
                    Text(sourceLabel(event.source), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.adjustEvent(event, 30) }) { Text("+30 min · ACT_THEN_TELL") }
                        TextButton(onClick = { vm.askDeleteEvent(event) }) { Text("Delete · ASK_FIRST") }
                    }
                }
            }
        }
        item { TasksUnavailableNotice() }
    }
}

@Composable
internal fun TasksUnavailableNotice() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Tasks", style = MaterialTheme.typography.titleLarge)
        Text("Task tracking is not available yet. Shiv does not create or track tasks.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MemoryTab(
    vm: ShivViewModel,
    name: String,
    onNameChange: (String) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val memories by vm.memories.collectAsStateWithLifecycle()
    val drafts by vm.drafts.collectAsStateWithLifecycle()
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("About you", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(name, onNameChange, label = { Text("What should Shiv call you?") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(notes, onNotesChange, label = { Text("Profile notes (kept on this phone)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Spacer(Modifier.height(6.dp))
            Button(onClick = { vm.saveProfile(name, notes) }) { Text("Save profile · FREE") }
        }
        item { Text("Remembered facts", style = MaterialTheme.typography.titleLarge) }
        if (memories.isEmpty()) item { MemoryEmptyNotice() }
        items(memories, key = { it.id }) { fact ->
            Card {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(fact.text, Modifier.weight(1f))
                    TextButton(onClick = { vm.askDeleteMemory(fact) }) { Text("Forget · ASK_FIRST") }
                }
            }
        }
        item { Text("Reply drafts", style = MaterialTheme.typography.titleLarge) }
        if (drafts.isEmpty()) item { Text("No drafts yet. Try ‘draft a reply to Raj: I can make it.’") }
        items(drafts, key = { it.id }) { draft ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("For ${draft.person}", fontWeight = FontWeight.SemiBold)
                    Text(draft.body)
                    TierChip(ActionTier.ACT_THEN_TELL.name)
                }
            }
        }
    }
}

@Composable
internal fun MemoryEmptyNotice() {
    Text("Tell Shiv ‘remember that…’ or share a preference to build your private memory.")
}

@Composable
private fun SettingsTab(
    vm: ShivViewModel,
    speakReplies: Boolean,
    onSpeakReplies: (Boolean) -> Unit,
    onOpenDestination: (ShivDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lead by vm.reminderLeadMinutes.collectAsStateWithLifecycle()
    val actionTiers by vm.actionTiers.collectAsStateWithLifecycle()
    val backupStatus by vm.backupStatus.collectAsStateWithLifecycle()
    val synced by vm.lastCalendarSync.collectAsStateWithLifecycle()
    val actionLog by vm.actionLog.collectAsStateWithLifecycle()
    val permissionStatus by rememberPermissionExplanationStatus()
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) vm.syncCalendar(context) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var listening by remember { mutableStateOf(context.getSharedPreferences("shiv_preferences", Context.MODE_PRIVATE).getBoolean("listening", false)) }
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) {
        startListening(context); listening = true
        context.getSharedPreferences("shiv_preferences", Context.MODE_PRIVATE).edit().putBoolean("listening", true).apply()
    } }
    var listeningNotice by remember { mutableStateOf<String?>(null) }
    val listeningNotificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
                startListening(context); listening = true
                context.getSharedPreferences("shiv_preferences", Context.MODE_PRIVATE).edit().putBoolean("listening", true).apply()
            } else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else listeningNotice = "Listening mode needs notification access so Android can show its ongoing status."
    }
    var backupDialogMode by remember { mutableStateOf<String?>(null) }
    var backupUri by remember { mutableStateOf<Uri?>(null) }
    var backupPassword by remember { mutableStateOf("") }
    val backupCreate = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) { backupUri = uri; backupDialogMode = "export" }
    }
    val backupOpen = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { backupUri = uri; backupDialogMode = "restore" }
    }
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Permissions & privacy", style = MaterialTheme.typography.headlineSmall) }
        item { Text("Shiv does nothing in the background unless you turn it on here. Your events and memories stay in the encrypted local database.") }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("More Shiv settings", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { onOpenDestination(ShivDestination.MODELS) }, modifier = Modifier.fillMaxWidth()) { Text("Manage local AI models") }
                OutlinedButton(onClick = { onOpenDestination(ShivDestination.AI_STATUS) }, modifier = Modifier.fillMaxWidth()) { Text("View AI status") }
                OutlinedButton(onClick = { onOpenDestination(ShivDestination.PERMISSIONS) }, modifier = Modifier.fillMaxWidth()) { Text("Permission explanations") }
            } }
        }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Calendar · read-only", style = MaterialTheme.typography.titleMedium)
                Text("Status: ${if (permissionStatus.calendar) "connected" else "not connected"}")
                Button(onClick = { if (hasPermission(context, Manifest.permission.READ_CALENDAR)) vm.syncCalendar(context) else calendarPermission.launch(Manifest.permission.READ_CALENDAR) }) { Text("Sync phone calendar · FREE") }
                synced?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            } }
        }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Notifications", style = MaterialTheme.typography.titleMedium)
                Text(if (permissionStatus.notifications) "Reminder notifications are allowed." else "Turn these on to see reminder alerts.")
                if (Build.VERSION.SDK_INT >= 33 && !hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)) Button(onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow notifications") }
                Text("Notification reading: ${if (permissionStatus.notificationReader) "enabled" else "off"}")
                OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } }) { Text("Choose apps Shiv may notice") }
            } }
        }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Listening mode", style = MaterialTheme.typography.titleMedium)
                Text("Uses on-device speech recognition while a visible notification is active. Shiv saves only extracted event details, never raw audio or transcripts. Get other people’s consent before recording/listening; recording laws vary. This cannot record the other side of a phone call, and Android may restrict microphone use during calls.")
                listeningNotice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = {
                    if (listening) {
                        context.stopService(Intent(context, app.shiv.ListeningService::class.java)); listening = false
                        context.getSharedPreferences("shiv_preferences", Context.MODE_PRIVATE).edit().putBoolean("listening", false).apply()
                    } else if (Build.VERSION.SDK_INT >= 33 && !hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)) listeningNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else if (hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
                        startListening(context); listening = true
                        context.getSharedPreferences("shiv_preferences", Context.MODE_PRIVATE).edit().putBoolean("listening", true).apply()
                    } else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text(if (listening) "Stop listening" else "Start listening · FREE") }
            } }
        }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Reminders & voice", style = MaterialTheme.typography.titleMedium)
                Text("Default: $lead minutes early. Tell Shiv ‘remind me 30 minutes early, not 10’ to change it.")
                Text("Exact alarm access: ${if (permissionStatus.exactAlarms) "allowed" else "off (alarms still use the Android fallback)"}")
                if (Build.VERSION.SDK_INT >= 31 && !permissionStatus.exactAlarms) OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) } }) { Text("Allow exact alarms") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Speak replies aloud", Modifier.weight(1f))
                    Switch(checked = speakReplies, onCheckedChange = onSpeakReplies)
                }
                Text("Spoken reminders use an installed offline voice. If your phone has no offline voice, Shiv shows a notification instead.", style = MaterialTheme.typography.bodySmall)
            } }
        }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Permission tiers", style = MaterialTheme.typography.titleMedium)
                Text("Choose the minimum safety level for each action. You can make actions stricter; destructive and people-facing actions always require confirmation.", style = MaterialTheme.typography.bodySmall)
                val controls = listOf(
                    Triple("CREATE_EVENT", "Save an event or reminder", ActionTier.FREE),
                    Triple("SET_REMINDER", "Change reminder timing rules", ActionTier.FREE),
                    Triple("SAVE_MEMORY", "Save a personal memory or rule", ActionTier.FREE),
                    Triple("SAVE_PROFILE", "Update your local profile", ActionTier.FREE),
                    Triple("RESCHEDULE", "Reschedule an event", ActionTier.ACT_THEN_TELL),
                    Triple("DRAFT_REPLY", "Save a reply draft", ActionTier.ACT_THEN_TELL),
                    Triple("READ_CALENDAR", "Read calendar events", ActionTier.FREE),
                    Triple("READ_NOTIFICATIONS", "Notice notification details", ActionTier.ASK_FIRST),
                    Triple("DELETE_DATA", "Delete data", ActionTier.ASK_FIRST),
                    Triple("SEND_MESSAGE", "Prepare a message to another person", ActionTier.ASK_FIRST)
                )
                controls.forEach { (key, title, base) ->
                    TierSettingRow(title, base, actionTiers.firstOrNull { it.actionKey == key }?.tier?.let { runCatching { ActionTier.valueOf(it) }.getOrNull() } ?: base) { vm.saveTier(key, it) }
                }
            } }
        }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Samsung background setting", style = MaterialTheme.typography.titleMedium)
                Text("To keep alarms and listening reliable: Settings → Battery → Background usage limits → Never sleeping apps → add Shiv. Samsung may still pause apps in power-saving mode.")
            } }
        }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Encrypted backup & restore", style = MaterialTheme.typography.titleMedium)
                Text("A passphrase encrypts your chats, profile, memories, events, rules, drafts, action history, and tier settings. The backup is independent of your phone’s Keystore key.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { backupCreate.launch("Shiv-Encrypted-Backup.shivbackup") }) { Text("Create backup · FREE") }
                    OutlinedButton(onClick = { backupOpen.launch(arrayOf("*/*")) }) { Text("Restore · ASK_FIRST") }
                }
                backupStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            } }
        }
        item { Text("Recent action history", style = MaterialTheme.typography.titleLarge) }
        if (actionLog.isEmpty()) item { Text("No actions have been recorded yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(actionLog, key = { it.id }) { entry ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(entry.action, Modifier.weight(1f), fontWeight = FontWeight.Medium); TierChip(entry.tier) }
                Text(entry.outcome, style = MaterialTheme.typography.bodySmall)
                Text(android.text.format.DateFormat.format("MMM d, h:mm a", entry.createdAt).toString(), style = MaterialTheme.typography.labelSmall)
            } }
        }
        item {
            OutlinedButton(onClick = { vm.askDeleteEverything() }, modifier = Modifier.fillMaxWidth()) { Text("Erase all Shiv data · ASK_FIRST") }
        }
    }
    backupDialogMode?.let { mode ->
        AlertDialog(
            onDismissRequest = { backupDialogMode = null; backupPassword = ""; backupUri = null },
            title = { Text(if (mode == "export") "Protect your backup" else "Restore encrypted backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (mode == "export") "Choose a passphrase with at least 12 characters. Shiv does not save or recover it." else "Restoring replaces the current local Shiv data. This cannot be undone. Enter the backup passphrase to confirm.")
                    OutlinedTextField(value = backupPassword, onValueChange = { backupPassword = it }, label = { Text("Backup passphrase") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                }
            },
            confirmButton = {
                Button(enabled = backupPassword.length >= (if (mode == "export") 12 else 1), onClick = {
                    val uri = backupUri
                    val secret = backupPassword.toCharArray()
                    backupDialogMode = null; backupPassword = ""; backupUri = null
                    if (uri != null) if (mode == "export") vm.exportBackup(uri, secret) else vm.restoreBackup(uri, secret)
                }) { Text(if (mode == "export") "Save encrypted backup" else "Replace data and restore") }
            },
            dismissButton = { TextButton(onClick = { backupDialogMode = null; backupPassword = ""; backupUri = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ModelManagementTab(vm: ShivViewModel, modifier: Modifier = Modifier) {
    val modelStatus by vm.modelStatus.collectAsStateWithLifecycle()
    val modelFiles by vm.modelFiles.collectAsStateWithLifecycle()
    val inventoryLoading by vm.modelInventoryLoading.collectAsStateWithLifecycle()
    val inventoryError by vm.modelInventoryError.collectAsStateWithLifecycle()
    var modelRole by rememberSaveable { mutableStateOf<String?>(null) }
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val role = modelRole
        if (uri != null && role != null) vm.importModel(uri, role)
        modelRole = null
    }

    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Local AI models", style = MaterialTheme.typography.headlineSmall) }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Status: $modelStatus", style = MaterialTheme.typography.titleMedium)
                ModelInventoryNotice(inventoryLoading, inventoryError, modelFiles.first, modelFiles.second, detailedEmptyText = true)
                if (modelStatus.startsWith("Copying")) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Import in progress")
                    }
                } else if (modelStatus.startsWith("Model import failed")) {
                    Text(modelStatus, color = MaterialTheme.colorScheme.error)
                }
                Text("Choose model files already on this phone. Shiv copies them into private app storage; files are not uploaded or bundled with the app.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { modelRole = "fast"; modelPicker.launch(arrayOf("*/*")) }) { Text("Import fast model") }
                    OutlinedButton(onClick = { modelRole = "chat"; modelPicker.launch(arrayOf("*/*")) }) { Text("Import chat model") }
                }
                Text("Model removal is not available in this screen yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
        }
    }
}

@Composable
private fun AiStatusTab(vm: ShivViewModel, modifier: Modifier = Modifier) {
    val status by vm.brainRuntimeStatus.collectAsStateWithLifecycle()
    val modelFiles by vm.modelFiles.collectAsStateWithLifecycle()
    val inventoryLoading by vm.modelInventoryLoading.collectAsStateWithLifecycle()
    val inventoryError by vm.modelInventoryError.collectAsStateWithLifecycle()
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("AI status", style = MaterialTheme.typography.headlineSmall) }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("On-device Brain", style = MaterialTheme.typography.titleMedium)
                Text(status.status)
                Text("Inference: ${if (status.inferenceRunning) "running" else "idle"}")
                Text("Requests waiting: ${status.queuedRequests}")
                Text("Last routing: ${status.lastRoutingMillis?.let { "$it ms" } ?: "not yet measured"}")
                Text("Last reply: ${status.lastReplyMillis?.let { "$it ms" } ?: "not yet measured"}")
            } }
        }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Installed models", style = MaterialTheme.typography.titleMedium)
                ModelInventoryNotice(inventoryLoading, inventoryError, modelFiles.first, modelFiles.second, detailedEmptyText = false)
            } }
        }
    }
}

@Composable
internal fun ModelInventoryNotice(loading: Boolean, error: String?, fastInstalled: Boolean, chatInstalled: Boolean, detailedEmptyText: Boolean) {
    when {
        loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("Checking private model storage…")
        }
        error != null -> Text(error, color = MaterialTheme.colorScheme.error)
        else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Fast model: ${if (fastInstalled) "installed locally" else "not installed"}")
            Text("Chat model: ${if (chatInstalled) "installed locally" else "not installed"}")
            if (!fastInstalled && !chatInstalled) {
                Text(
                    if (detailedEmptyText) "No models are installed. Chat answers that require a language model will be unavailable until you import one."
                    else "No model is installed. Deterministic event and reminder features remain available."
                )
            } else {
                Text("Installed does not mean loaded. The runtime reports whether inference has started.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

internal data class PermissionExplanationStatus(
    val calendar: Boolean,
    val notifications: Boolean,
    val notificationReader: Boolean,
    val microphone: Boolean,
    val camera: Boolean,
    val exactAlarms: Boolean
)

internal fun notificationCapabilityGranted(apiLevel: Int, appNotificationsEnabled: Boolean, postNotificationsGranted: Boolean): Boolean =
    appNotificationsEnabled && (apiLevel < 33 || postNotificationsGranted)

private fun readPermissionExplanationStatus(context: Context): PermissionExplanationStatus {
    val apiLevel = Build.VERSION.SDK_INT
    val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val postNotificationsGranted = apiLevel < 33 || hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
    val exactAlarmsAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    } else true
    val notificationReaderEnabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty().contains(context.packageName)
    return PermissionExplanationStatus(
        calendar = hasPermission(context, Manifest.permission.READ_CALENDAR),
        notifications = notificationCapabilityGranted(apiLevel, notificationsEnabled, postNotificationsGranted),
        notificationReader = notificationReaderEnabled,
        microphone = hasPermission(context, Manifest.permission.RECORD_AUDIO),
        camera = hasPermission(context, Manifest.permission.CAMERA),
        exactAlarms = exactAlarmsAllowed
    )
}

@Composable
private fun rememberPermissionExplanationStatus(
    statusProvider: (() -> PermissionExplanationStatus)? = null
): State<PermissionExplanationStatus> {
    val context = LocalContext.current
    val readStatus = remember(context, statusProvider) { statusProvider ?: { readPermissionExplanationStatus(context) } }
    val status = remember(context, statusProvider) { mutableStateOf(readStatus()) }
    val currentReadStatus by rememberUpdatedState(readStatus)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) status.value = currentReadStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return status
}

@Composable
internal fun PermissionExplanationTab(
    modifier: Modifier = Modifier,
    statusProvider: (() -> PermissionExplanationStatus)? = null
) {
    val context = LocalContext.current
    val status by rememberPermissionExplanationStatus(statusProvider)
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Permission explanations", style = MaterialTheme.typography.headlineSmall) }
        item { Text("These permissions unlock optional features. Shiv does not grant access automatically; you control each permission in Android.") }
        item { PermissionExplanation("Calendar", "Read calendar entries only when you choose to sync them. Shiv does not write to your calendar.", status.calendar) }
        item { PermissionExplanation("Notifications", "Show reminder alerts and the visible status for listening mode.", status.notifications) }
        item { PermissionExplanation("Notification access", "Read notifications you explicitly allow so Shiv can offer relevant event candidates.", status.notificationReader) }
        item { PermissionExplanation("Microphone", "Use one-shot on-device speech recognition or the listening service you start.", status.microphone) }
        item { PermissionExplanation("Camera", "Capture a photo only after you tap the camera control.", status.camera) }
        item { PermissionExplanation("Exact alarms", "Schedule reminders at their requested time. Android's inexact fallback remains available when exact alarms are disabled.", status.exactAlarms) }
        item {
            OutlinedButton(
                onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) } },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Manage Shiv permissions in Android settings") }
        }
    }
}

@Composable
private fun PermissionExplanation(title: String, explanation: String, granted: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (granted) "Allowed" else "Not allowed",
                    modifier = if (title == "Notifications") Modifier.testTag("permission-notifications-status") else Modifier,
                    color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(explanation, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun TierChip(tier: String) {
    Surface(
        modifier = Modifier.testTag("tier-badge-$tier"),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(tier, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TierExplanation(tier: String, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically) { TierChip(tier); Spacer(Modifier.width(8.dp)); Text(description, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun TierSettingRow(title: String, minimum: ActionTier, selected: ActionTier, onSelect: (ActionTier) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box {
            TextButton(onClick = { expanded = true }) { Text(selected.name) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ActionTier.entries.filter { it.ordinal >= minimum.ordinal }.forEach { tier ->
                    DropdownMenuItem(text = { Text(tier.name) }, onClick = { onSelect(tier); expanded = false })
                }
            }
        }
    }
}

private fun hasPermission(context: Context, permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun startListening(context: Context) {
    runCatching { ContextCompat.startForegroundService(context, Intent(context, app.shiv.ListeningService::class.java)) }
}

private fun sourceLabel(source: String) = when (source) {
    "CALENDAR" -> "From phone calendar"
    "NOTIFICATION" -> "Detected in a notification"
    "LISTENING" -> "Detected while listening"
    else -> "Added in chat"
}
