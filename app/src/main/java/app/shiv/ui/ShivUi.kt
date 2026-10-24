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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.shiv.domain.speech.SpeechRecognitionSession
import app.shiv.domain.speech.SpeechSynthesis

private enum class ShivTab(val label: String) { CHAT("Chat"), EVENTS("Events"), MEMORY("Memory"), SETTINGS("Settings") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShivApp(vm: ShivViewModel = viewModel()) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(ShivTab.CHAT) }
    val messages by vm.messages.collectAsStateWithLifecycle()
    val pending by vm.pendingAction.collectAsStateWithLifecycle()
    val speakReplies = remember { mutableStateOf(context.getSharedPreferences("shiv_preferences", Context.MODE_PRIVATE).getBoolean("speak_replies", true)) }
    val speaker: SpeechSynthesis = remember { OfflineSpeechOutput(context) }
    var lastSpokenId by remember { mutableStateOf<String?>(null) }
    DisposableEffect(speaker) { onDispose { speaker.close() } }
    LaunchedEffect(messages.lastOrNull()?.id, speakReplies.value) {
        val latest = messages.lastOrNull()
        if (latest != null && !latest.fromUser && latest.id != lastSpokenId) {
            lastSpokenId = latest.id
            if (speakReplies.value) speaker.speak(latest.text)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Shiv"); Text("Your personal manager", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            )
        },
        bottomBar = {
            NavigationBar {
                ShivTab.entries.forEach { tab ->
                    val icon = when (tab) { ShivTab.CHAT -> Icons.Default.Chat; ShivTab.EVENTS -> Icons.Default.Event; ShivTab.MEMORY -> Icons.Default.Bookmark; ShivTab.SETTINGS -> Icons.Default.Settings }
                    NavigationBarItem(selected = selectedTab == tab, onClick = { selectedTab = tab }, icon = { Icon(icon, contentDescription = tab.label) }, label = { Text(tab.label) })
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                ShivTab.CHAT -> ChatTab(vm, messages, Modifier.fillMaxSize())
                ShivTab.EVENTS -> EventsTab(vm, Modifier.fillMaxSize())
                ShivTab.MEMORY -> MemoryTab(vm, Modifier.fillMaxSize())
                ShivTab.SETTINGS -> SettingsTab(vm, speakReplies.value, { value ->
                    speakReplies.value = value
                    context.getSharedPreferences("shiv_preferences", Context.MODE_PRIVATE).edit().putBoolean("speak_replies", value).apply()
                }, Modifier.fillMaxSize())
            }
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

@Composable
private fun ChatTab(vm: ShivViewModel, messages: List<ChatMessage>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }
    var showCamera by remember { mutableStateOf(false) }
    var micNotice by remember { mutableStateOf<String?>(null) }
    var photoNotice by remember { mutableStateOf<String?>(null) }
    var speechInput by remember { mutableStateOf<SpeechRecognitionSession?>(null) }

    fun startMic() {
        val session = SpeechInput(context, onText = { transcript ->
            draft = transcript
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
            if (messages.isEmpty()) item {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Hey, I’m Shiv.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text("Your loyal, slightly nosy manager. Tell me about a meeting, a promise, or something you’d like me to remember. Your data stays on this phone.")
                    }
                }
            }
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
            OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f), placeholder = { Text("Message Shiv…") }, shape = RoundedCornerShape(24.dp), maxLines = 4)
            IconButton(onClick = { vm.send(draft); draft = "" }, enabled = draft.isNotBlank()) { Icon(Icons.Default.Send, "Send") }
        }
    }
    if (showCamera) Dialog(onDismissRequest = { showCamera = false }) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            PhotoCapture(onClose = { showCamera = false }, onDescription = { description -> photoNotice = description; vm.appendAssistant(description) })
        }
    }
}

@Composable
private fun EventsTab(vm: ShivViewModel, modifier: Modifier = Modifier) {
    val events by vm.events.collectAsStateWithLifecycle()
    val lead by vm.reminderLeadMinutes.collectAsStateWithLifecycle()
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column {
                Text("Upcoming", style = MaterialTheme.typography.headlineSmall)
                Text("Shiv reminds you $lead minutes early. Tell him if you want to change that.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
    }
}

@Composable
private fun MemoryTab(vm: ShivViewModel, modifier: Modifier = Modifier) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val memories by vm.memories.collectAsStateWithLifecycle()
    val drafts by vm.drafts.collectAsStateWithLifecycle()
    var name by remember(profile?.updatedAt) { mutableStateOf(profile?.name.orEmpty()) }
    var notes by remember(profile?.updatedAt) { mutableStateOf(profile?.notes.orEmpty()) }
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("About you", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(name, { name = it }, label = { Text("What should Shiv call you?") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(notes, { notes = it }, label = { Text("Profile notes (kept on this phone)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Spacer(Modifier.height(6.dp))
            Button(onClick = { vm.saveProfile(name, notes) }) { Text("Save profile · FREE") }
        }
        item { Text("Remembered facts", style = MaterialTheme.typography.titleLarge) }
        if (memories.isEmpty()) item { Text("Tell Shiv ‘remember that…’ or share a preference to build your private memory.") }
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
private fun SettingsTab(vm: ShivViewModel, speakReplies: Boolean, onSpeakReplies: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lead by vm.reminderLeadMinutes.collectAsStateWithLifecycle()
    val modelStatus by vm.modelStatus.collectAsStateWithLifecycle()
    val modelFiles by vm.modelFiles.collectAsStateWithLifecycle()
    val actionTiers by vm.actionTiers.collectAsStateWithLifecycle()
    val backupStatus by vm.backupStatus.collectAsStateWithLifecycle()
    val synced by vm.lastCalendarSync.collectAsStateWithLifecycle()
    val actionLog by vm.actionLog.collectAsStateWithLifecycle()
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
    val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
    val notificationsEnabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    val exactAlarms = Build.VERSION.SDK_INT < 31 || (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    var modelRole by remember { mutableStateOf<String?>(null) }
    var backupDialogMode by remember { mutableStateOf<String?>(null) }
    var backupUri by remember { mutableStateOf<Uri?>(null) }
    var backupPassword by remember { mutableStateOf("") }
    val backupCreate = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) { backupUri = uri; backupDialogMode = "export" }
    }
    val backupOpen = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { backupUri = uri; backupDialogMode = "restore" }
    }
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val role = modelRole
        if (uri != null && role != null) vm.importModel(uri, role)
        modelRole = null
    }

    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Permissions & privacy", style = MaterialTheme.typography.headlineSmall) }
        item { Text("Shiv does nothing in the background unless you turn it on here. Your events and memories stay in the encrypted local database.") }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Calendar · read-only", style = MaterialTheme.typography.titleMedium)
                Text("Status: ${if (hasPermission(context, Manifest.permission.READ_CALENDAR)) "connected" else "not connected"}")
                Button(onClick = { if (hasPermission(context, Manifest.permission.READ_CALENDAR)) vm.syncCalendar(context) else calendarPermission.launch(Manifest.permission.READ_CALENDAR) }) { Text("Sync phone calendar · FREE") }
                synced?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            } }
        }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Notifications", style = MaterialTheme.typography.titleMedium)
                Text(if (notificationsEnabled) "Reminder notifications are allowed." else "Turn these on to see reminder alerts.")
                if (Build.VERSION.SDK_INT >= 33 && !notificationsEnabled) Button(onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow notifications") }
                Text("Notification reading: ${if (enabledListeners.contains(context.packageName)) "enabled" else "off"}")
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
                Text("Exact alarm access: ${if (exactAlarms) "allowed" else "off (alarms still use the Android fallback)"}")
                if (Build.VERSION.SDK_INT >= 31 && !exactAlarms) OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) } }) { Text("Allow exact alarms") }
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
                Text("Local AI models", style = MaterialTheme.typography.titleMedium)
                Text("Status: $modelStatus")
                Text("Fast model: ${if (modelFiles.first) "installed" else "not installed"} · Chat model: ${if (modelFiles.second) "installed" else "not installed"}")
                Text("Import .litertlm model files from your phone. Shiv copies them into private app storage. Model files stay on this device; there is no AI network permission or API key.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { modelRole = "fast"; modelPicker.launch(arrayOf("*/*")) }) { Text("Choose fast model") }
                    OutlinedButton(onClick = { modelRole = "chat"; modelPicker.launch(arrayOf("*/*")) }) { Text("Choose chat model") }
                }
                Text("Room + SQLCipher database is encrypted with a random key wrapped by Android Keystore. Photo labels and speech recognition use on-device components.", style = MaterialTheme.typography.bodySmall)
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
private fun TierChip(tier: String) {
    AssistChip(onClick = {}, label = { Text(tier) })
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
