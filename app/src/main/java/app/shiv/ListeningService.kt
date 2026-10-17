package app.shiv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** User-started, visible, on-device transcription. Transcripts are transient and never stored. */
class ListeningService : Service() {
    private var transcriber: Brain.OnDeviceTranscriber? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val brain = Brain()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        val notification = listeningNotification()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        if (transcriber == null) beginListening()
        return START_NOT_STICKY
    }

    private fun beginListening() {
        transcriber = brain.createOnDeviceTranscriber(
            applicationContext,
            onText = { transientText ->
                saveOnlyExtractedEvent(transientText)
                handler.postDelayed({ startRecognition() }, 400L)
            },
            onRecognitionError = { error ->
                if (error == 9) stopSelf() else handler.postDelayed({ startRecognition() }, 1_500L)
            }
        )
        if (transcriber == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        scope.launch { ShivDatabase.get(applicationContext).dao().logAction(ActionLogEntry(action = "Started on-device listening mode", tier = ActionTier.FREE.name, outcome = "Audio and transcript are not stored")) }
        startRecognition()
    }

    private fun startRecognition() {
        try { transcriber?.start() } catch (_: SecurityException) { stopSelf() } catch (_: IllegalStateException) { }
    }

    private fun saveOnlyExtractedEvent(transcript: String) {
        val event = brain.extractEvent(transcript, source = "LISTENING") ?: return
        scope.launch {
            val dao = ShivDatabase.get(applicationContext).dao()
            dao.saveEvent(event)
            dao.logAction(ActionLogEntry(action = "Saved an event from listening mode", tier = ActionTier.FREE.name, outcome = event.title))
            val lead = dao.reminderRule(Brain.REMINDER_LEAD_KEY)?.value?.toIntOrNull() ?: Brain.DEFAULT_LEAD_MINUTES
            AlarmScheduler.schedule(applicationContext, event, lead)
        }
        // No transcript or audio buffer is written to disk or the database.
    }

    private fun listeningNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Shiv listening mode", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 3, Intent(this, ListeningService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Shiv is listening on this phone")
            .setContentText("Only detected events are saved. Tap Stop to end listening.")
            .addAction(0, "Stop listening", stop)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        transcriber?.close()
        transcriber = null
        getSharedPreferences("shiv_preferences", Context.MODE_PRIVATE).edit().putBoolean("listening", false).apply()
        scope.launch { ShivDatabase.get(applicationContext).dao().logAction(ActionLogEntry(action = "Stopped listening mode", tier = ActionTier.FREE.name, outcome = "Microphone released")) }
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "app.shiv.STOP_LISTENING"
        const val CHANNEL_ID = "shiv_listening"
        const val NOTIFICATION_ID = 43
    }
}
