package com.shiv.assistant

import android.content.Intent
import android.os.Bundle
import com.shiv.assistant.voice.ShivVoice
import android.content.pm.PackageManager
import android.Manifest
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import com.shiv.assistant.ui.ShivInterface
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.shiv.assistant.voice.SpeechChunker
import com.shiv.assistant.data.EncryptedDatabase
import com.shiv.assistant.data.ShivVault
import com.shiv.assistant.data.OwnerStateRepository
import com.shiv.assistant.ui.StorageRecoveryScreen
import com.shiv.assistant.ui.StorageCheckingScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import com.shiv.assistant.data.ShivRepository

class MainActivity : ComponentActivity() {
    @Volatile private var storageReadyForTriggers=false
    private var activeController:ShivController?=null
    override fun onResume() {
        super.onResume()
        activeController?.refreshPostCall()
    }
    private fun recordAppOpen() {
        if(!storageReadyForTriggers)return
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                if(ShivVault(applicationContext).get("proactive_opt_in")=="yes" &&
                    !com.shiv.assistant.awareness.AwarenessControl.isPaused(applicationContext))
                    ShivRepository(applicationContext).incoming("app_open","Owner opened Shiv",2)
            }
        }
    }
    override fun onStart() {
        super.onStart()
        recordAppOpen()
    }
    private fun storageProblem():String? = try {
        EncryptedDatabase.verify(applicationContext)
        ShivVault(applicationContext) // Completes or safely retries the legacy migration.
        OwnerStateRepository(applicationContext).load() // Validate essential owner records.
        null
    } catch (_:Exception) {
        getSharedPreferences("shiv_awareness_control",MODE_PRIVATE).edit()
            .putBoolean("paused",true).commit()
        "Encrypted Shiv data cannot be opened. Your records were not replaced."
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var storageIssue by mutableStateOf<String?>(null)
        var storageChecked by mutableStateOf(false)
        setContent {
            val scope = rememberCoroutineScope()
            LaunchedEffect(Unit) {
                storageIssue=withContext(Dispatchers.IO) { storageProblem() }
                storageReadyForTriggers=storageIssue==null
                if(storageReadyForTriggers)recordAppOpen()
                storageChecked=true
            }
            if(!storageChecked) {
                StorageCheckingScreen()
                return@setContent
            }
            if(storageIssue!=null) {
                StorageRecoveryScreen(storageIssue.orEmpty(),onRetry={
                    scope.launch {
                        storageChecked=false
                        storageIssue=withContext(Dispatchers.IO) { storageProblem() }
                        storageReadyForTriggers=storageIssue==null
                        if(storageReadyForTriggers)recordAppOpen()
                        storageChecked=true
                    }
                },onErase={ phrase ->
                    scope.launch {
                        try {
                            storageChecked=false
                            EncryptedDatabase.resetAfterOwnerConfirmation(applicationContext,phrase)
                            storageIssue=withContext(Dispatchers.IO) { storageProblem() }
                            storageReadyForTriggers=storageIssue==null
                            if(storageReadyForTriggers)recordAppOpen()
                        } catch (_:Exception) {
                            storageIssue="Storage recovery did not finish. Review any backup before retrying."
                            storageReadyForTriggers=false
                        } finally { storageChecked=true }
                    }
                })
                return@setContent
            }
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    com.shiv.assistant.automation.SignalWorker.recoverPending(applicationContext)
                    com.shiv.assistant.automation.ShivReminders.recoverPending(applicationContext)
                }
                com.shiv.assistant.awareness.AwarenessControl.show(applicationContext)
                com.shiv.assistant.automation.ShivRetentionWorker.schedule(applicationContext)
                com.shiv.assistant.automation.ShivAgendaNudgeWorker.updateRegistration(
                    applicationContext,ShivVault(applicationContext).get("proactive_opt_in")=="yes")
                com.shiv.assistant.automation.ProactiveTriggerWorker.updateRegistration(
                    applicationContext,ShivVault(applicationContext).get("proactive_opt_in")=="yes")
                com.shiv.assistant.automation.CalendarChangeWorker.updateRegistration(
                    applicationContext,ShivVault(applicationContext).get(
                        com.shiv.assistant.automation.CalendarChangeWorker.OPT_IN)=="yes")
            }
            val controller = viewModel<ShivViewModel>().controller
            activeController=controller
            LaunchedEffect(controller) {
                withContext(Dispatchers.IO) { controller.phase.hub.restoreRouterIfSelected() }
            }
            var voiceJob by remember { mutableStateOf<Job?>(null) }
            val speech = remember { ShivVoice(applicationContext).also {
                it.initSpeech { ready, error ->
                    if(!ready) controller.showNotice(error ?: "Google TTS engine not available")
                }
            } }
            DisposableEffect(speech) { onDispose { speech.close() } }
            val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if(!granted) controller.showNotice("Microphone denied; offline recognition not started")
            }
            val beginListening = {
                voiceJob?.cancel()
                speech.stopSpeech()
                controller.speechDraft=""
                if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
                    microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    speech.startRecognition(controller.speechLocale,
                        onPartial={ controller.speechDraft=it.take(500) },
                        onResult={ heard ->
                            controller.speechDraft=""
                            if(heard.isNotBlank()) voiceJob=scope.launch {
                                val chunker=SpeechChunker()
                                var streamed=false
                                var speechFailed=false
                                controller.submit(heard) { fragment ->
                                    streamed=true
                                    for(chunk in chunker.accept(fragment)) {
                                        withContext(Dispatchers.Main) {
                                            if(!speechFailed) speech.speakQueued(chunk,controller.speechLocale)
                                                ?.let { controller.showNotice(it);speechFailed=true }
                                        }
                                    }
                                }
                                if(isActive) {
                                    val tail=chunker.finish()
                                    if(streamed) {
                                        if(tail.isNotEmpty() && !speechFailed)
                                            speech.speakQueued(tail,controller.speechLocale)
                                                ?.let(controller::showNotice)
                                    } else controller.messages.lastOrNull { it.role=="assistant" }
                                        ?.let { speech.speak(it.text,controller.speechLocale)
                                            ?.let(controller::showNotice) }
                                }
                            }
                        },
                        onFailure={ controller.speechDraft="";controller.showNotice(it) })
                }
            }
            val photo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
                controller.chooseImage(it)
            }
            val document = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
                if (it != null) scope.launch { controller.importText(it) }
            }
            val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                controller.phase.selectModel(uri)
            }
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            MaterialTheme(colorScheme = darkColorScheme(
                background = Color(0xFF070C18), surface = Color(0xFF111B2B),
                primary = Color(0xFF65F5F0), secondary = Color(0xFFA693FF),
                onBackground = Color(0xFFF2F7FF), onSurface = Color(0xFFF2F7FF)
            )) {
                ShivInterface(
                    controller = controller,
                    onVoice = { beginListening() },
                    onStopVoice = { voiceJob?.cancel();speech.stopSpeech();speech.stopListening() },
                    onPhoto = { photo.launch("image/*") },
                    onDocument = { document.launch("text/*") },
                    onSpeak = { spoken -> speech.speak(spoken,controller.speechLocale)?.let(controller::showNotice) },
                    onModelPick = { modelPicker.launch(arrayOf("*/*")) },
                    onNotificationPermission = {
                        if (android.os.Build.VERSION.SDK_INT >= 33)
                            permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
            }
        }
    }
}
