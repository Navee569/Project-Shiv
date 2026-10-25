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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val controller = remember { ShivController(applicationContext) }
            val scope = rememberCoroutineScope()
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
                if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
                    microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    speech.startRecognition(controller.speechLocale,
                        onPartial={},
                        onResult={ heard -> if(heard.isNotBlank()) scope.launch { controller.submit(heard) } },
                        onFailure={ controller.showNotice(it) })
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
