package app.shiv.platform

import app.shiv.ui.MainActivity
import app.shiv.*

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.util.Locale

class ReminderVoiceService : Service(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var eventTitle: String = "Your event is coming up"
    private var startAt: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        eventTitle = intent?.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: eventTitle
        startAt = intent?.getLongExtra(AlarmScheduler.EXTRA_START, 0L) ?: 0L
        val text = "Shiv reminder: $eventTitle is coming up${if (startAt > 0) " at ${android.text.format.DateFormat.format("h:mm a", startAt)}" else ""}."
        val notification = reminderNotification(text)
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, SERVICE_ID, notification, type)
        tts = TextToSpeech(this, this)
        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) { stopSelf(); return }
        val engine = tts ?: run { stopSelf(); return }
        engine.language = Locale.getDefault()
        val localVoice = engine.voices?.firstOrNull { !it.isNetworkConnectionRequired && it.locale.language == Locale.getDefault().language }
        if (localVoice == null) {
            ReminderReceiver.showNotification(this, eventTitle, startAt)
            stopSelf()
            return
        }
        engine.voice = localVoice
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { stopSelf() }
            @Deprecated("Deprecated by Android") override fun onError(utteranceId: String?) { stopSelf() }
        })
        val timeText = if (startAt > 0) " at ${android.text.format.DateFormat.format("h:mm a", startAt)}" else ""
        val speechText = "Shiv reminder: $eventTitle is coming up$timeText."
        engine.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "shiv-reminder-${System.currentTimeMillis()}")
    }

    private fun reminderNotification(text: String): Notification {
        val channel = ReminderReceiver.CHANNEL_ID
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(android.app.NotificationChannel(channel, "Shiv reminders", android.app.NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, channel).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Shiv is speaking").setContentText(text).setContentIntent(open).setOngoing(true).build()
    }

    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }
    private companion object { const val SERVICE_ID = 42 }
}
