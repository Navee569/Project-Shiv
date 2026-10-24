package app.shiv

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "Your upcoming event"
        val start = intent.getLongExtra(AlarmScheduler.EXTRA_START, 0L)
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { ShivDatabase.get(context).dao().logAction(ActionLogEntry(action = "Delivered scheduled reminder", tier = ActionTier.FREE.name, outcome = title)) }
            finally { pendingResult.finish() }
        }
        val spoken = context.getSharedPreferences("shiv_preferences", Context.MODE_PRIVATE).getBoolean("speak_reminders", true)
        if (spoken) {
            ContextCompat.startForegroundService(context, Intent(context, ReminderVoiceService::class.java).putExtra(AlarmScheduler.EXTRA_TITLE, title).putExtra(AlarmScheduler.EXTRA_START, start))
        } else {
            showNotification(context, title, start)
        }
    }

    companion object {
        const val CHANNEL_ID = "shiv_reminders"
        fun showNotification(context: Context, title: String, start: Long) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Shiv reminders", NotificationManager.IMPORTANCE_HIGH))
            val open = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val whenText = if (start > 0) android.text.format.DateFormat.format("EEE, h:mm a", start) else "soon"
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Shiv reminder")
                .setContentText("$title · $whenText")
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) manager.notify((title + start).hashCode(), notification)
        }
    }
}
