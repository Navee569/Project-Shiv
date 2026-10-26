package com.shiv.assistant

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
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
import com.shiv.assistant.ui.ShivInterface
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Resume encrypted pending signals after process death; no silent background sensing.
        WorkManager.getInstance(this).enqueueUniqueWork(
            "shiv_encrypted_signal_worker", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ShivSignalWorker>().build())
        setContent {
            val controller = remember { ShivController(applicationContext) }
            val scope = rememberCoroutineScope()
            val tts = remember { TextToSpeech(applicationContext, null, "com.google.android.tts") }
            DisposableEffect(tts) { onDispose { tts.stop(); tts.shutdown() } }
            val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val words = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val heard = words?.firstOrNull()
                if (!heard.isNullOrBlank()) scope.launch { controller.submit(heard) }
            }
            val photo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
                controller.chooseImage(it)
            }
            val document = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
                if (it != null) scope.launch { controller.importText(it) }
            }
            val model = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
                if (it != null) scope.launch { controller.registerModel(it) }
            }
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            MaterialTheme(colorScheme = darkColorScheme(
                background = Color(0xFF070C18), surface = Color(0xFF111B2B),
                primary = Color(0xFF65F5F0), secondary = Color(0xFFA693FF),
                onBackground = Color(0xFFF2F7FF), onSurface = Color(0xFFF2F7FF)
            )) {
                ShivInterface(
                    controller = controller,
                    onVoice = {
                        try {
                            voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
                        } catch (_: Exception) { /* recognizer availability is owner-verify */ }
                    },
                    onPhoto = { photo.launch("image/*") },
                    onDocument = { document.launch("text/*") },
                    onModel = { model.launch(arrayOf("application/octet-stream", "*/*")) },
                    onSpeak = { spoken -> tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "shiv_response") },
                    onNotificationPermission = {
                        if (android.os.Build.VERSION.SDK_INT >= 33)
                            permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
            }
        }
    }
}
