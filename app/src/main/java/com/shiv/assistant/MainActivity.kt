package com.shiv.assistant

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import java.util.Locale
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.shiv.assistant.automation.ShivSignalWorker
import com.shiv.assistant.automation.ShivConsolidation
import com.shiv.assistant.automation.ShivProactive
import com.shiv.assistant.ui.ShivInterface
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private var ownerController: ShivController? = null
    private var afterAuthentication: (() -> Unit)? = null
    private var recognizer: SpeechRecognizer? = null

    private fun startOnDeviceRecognition(locale: String, onText: (String) -> Unit,
        onError: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < 31 ||
            !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            onError("On-device speech recognition unavailable. Install the language pack in Speech Services by Google.")
            return
        }
        recognizer?.destroy()
        recognizer = try { SpeechRecognizer.createOnDeviceSpeechRecognizer(this) }
        catch (_: Exception) {
            onError("Offline recognizer unavailable")
            return
        }
        val service = recognizer ?: return
        service.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                onError("Offline STT failed (code " + error + "). Verify the " + locale + " offline language pack.")
                recognizer?.destroy(); recognizer = null
            }
            override fun onResults(results: Bundle?) {
                val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val sentence = words?.firstOrNull()
                recognizer?.destroy(); recognizer = null
                if (sentence.isNullOrBlank()) onError("No speech recognized")
                else onText(sentence)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        service.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true))
    }

    override fun onResume() {
        super.onResume()
        ownerController?.refreshPermissions()
    }

    override fun onStop() {
        ownerController?.lockPrivate()
        super.onStop()
    }

    override fun onDestroy() {
        afterAuthentication = null
        ownerController?.dispose()
        ownerController = null
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Resume encrypted pending signals after process death; no silent background sensing.
        WorkManager.getInstance(this).enqueueUniqueWork(
            "shiv_encrypted_signal_worker", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ShivSignalWorker>().build())
        ShivConsolidation.schedule(this)
        ShivProactive.schedule(this)
        setContent {
            val controller = remember { ShivController(applicationContext) }
            ownerController = controller
            val prompt = remember {
                BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            val callback = afterAuthentication
                            afterAuthentication = null
                            callback?.invoke()
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            afterAuthentication = null
                            controller.speechMessage("Authentication canceled or unavailable")
                        }
                    })
            }
            val authenticate: (() -> Unit) -> Unit = { approved ->
                val authenticators = if (Build.VERSION.SDK_INT >= 30)
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                else BiometricManager.Authenticators.BIOMETRIC_WEAK
                if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
                    controller.speechMessage("Set up a phone lock screen and fingerprint to unlock private actions.")
                } else {
                    afterAuthentication = approved
                    val info = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Shiv private verification")
                        .setSubtitle("Confirm access to your private data or high-risk action")
                        .setAllowedAuthenticators(authenticators)
                    if (Build.VERSION.SDK_INT < 30) info.setNegativeButtonText("CANCEL")
                    prompt.authenticate(info.build())
                }
            }
            val scope = rememberCoroutineScope()
            val tts = remember { TextToSpeech(applicationContext, null, "com.google.android.tts") }
            DisposableEffect(tts) { onDispose { tts.stop(); tts.shutdown() } }
            val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
                if (allowed) startOnDeviceRecognition(controller.speechLocale,
                    onText = { scope.launch { controller.submit(it) } },
                    onError = { controller.speechMessage(it) })
                else controller.speechMessage("Microphone permission denied. Voice input was not started.")
            }
            val photo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
                controller.chooseImage(it)
            }
            val offlineOcr = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                if (uri != null) scope.launch { controller.scanOfflineImage(uri) }
            }
            val document = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
                if (it != null) scope.launch { controller.importText(it) }
            }
            var pendingBackupPhrase by remember { mutableStateOf("") }
            val backupExport = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
                val phrase = pendingBackupPhrase
                pendingBackupPhrase = ""
                if (uri != null && phrase.length >= 12) {
                    // File picker can stop the activity, which correctly relocks memory.
                    // Explicit reauthentication is required before any data export.
                    authenticate {
                        controller.onBiometricAuthenticated()
                        scope.launch { controller.exportEncryptedBackup(uri, phrase) }
                    }
                }
            }
            val backupRestore = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()) { uri ->
                val phrase = pendingBackupPhrase
                pendingBackupPhrase = ""
                if (uri != null && phrase.length >= 12) {
                    authenticate {
                        controller.onBiometricAuthenticated()
                        scope.launch { controller.restoreEncryptedBackup(uri, phrase) }
                    }
                }
            }
            val model = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
                if (it != null) scope.launch { controller.registerModel(it) }
            }
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            val calendarPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) controller.previewDeviceCalendar()
                else controller.speechMessage("Calendar read permission denied")
            }
            var pendingContactQuery by remember { mutableStateOf("") }
            val phonePermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()) { granted ->
                controller.updatePostCallOptIn(granted)
            }
            val contactsPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) controller.searchDeviceContacts(pendingContactQuery)
                else controller.speechMessage("Contacts read permission denied")
            }
            MaterialTheme(colorScheme = darkColorScheme(
                background = Color(0xFF070C18), surface = Color(0xFF111B2B),
                primary = Color(0xFF65F5F0), secondary = Color(0xFFA693FF),
                onBackground = Color(0xFFF2F7FF), onSurface = Color(0xFFF2F7FF)
            )) {
                ShivInterface(
                    controller = controller,
                    onVoice = {
                        if (controller.sensingPaused || controller.guestMode || controller.privacyZone) {
                            controller.speechMessage("Sensing is paused. Switch it back on in Privacy.")
                        } else {
                            tts.stop()
                            scope.launch { controller.interruptInference() }
                        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED) {
                            startOnDeviceRecognition(controller.speechLocale,
                                onText = { scope.launch { controller.submit(it) } },
                                onError = { controller.speechMessage(it) })
                        } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onPhoto = { photo.launch("image/*") },
                    onOfflineOcr = { offlineOcr.launch("image/*") },
                    onDocument = { document.launch("text/*") },
                    onModel = { model.launch(arrayOf("application/octet-stream", "*/*")) },
                    onUnlockMemory = { authenticate { controller.onBiometricAuthenticated() } },
                    onApproveHighRisk = { authenticate { controller.approve() } },
                    onBackupExport = { phrase ->
                        pendingBackupPhrase = phrase
                        backupExport.launch("Shiv-private.shivbak")
                    },
                    onBackupRestore = { phrase ->
                        pendingBackupPhrase = phrase
                        backupRestore.launch(arrayOf("application/octet-stream", "*/*"))
                    },
                    onSpeak = { spoken ->
                        val locale = Locale.forLanguageTag(controller.speechLocale)
                        val available = tts.isLanguageAvailable(locale)
                        if (available >= TextToSpeech.LANG_AVAILABLE) {
                            tts.language = locale
                            tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "shiv_response")
                        } else controller.speechMessage(
                            "Voice data unavailable for " + controller.speechLocale +
                            ". Install Speech Services by Google voice data in Android Settings > General management > Text-to-speech."
                        )
                    },
                    onPostCallPermission = {
                        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
                            PackageManager.PERMISSION_GRANTED) controller.updatePostCallOptIn(true)
                        else phonePermission.launch(Manifest.permission.READ_PHONE_STATE)
                    },
                    onCalendarPreview = {
                        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
                            PackageManager.PERMISSION_GRANTED) controller.previewDeviceCalendar()
                        else calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                    },
                    onContactLookup = { query ->
                        pendingContactQuery = query
                        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
                            PackageManager.PERMISSION_GRANTED) controller.searchDeviceContacts(query)
                        else contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                    },
                    onNotificationPermission = {
                        if (android.os.Build.VERSION.SDK_INT >= 33)
                            permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
            }
        }
    }
}
