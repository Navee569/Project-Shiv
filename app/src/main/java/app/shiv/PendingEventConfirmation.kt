package app.shiv

import app.shiv.data.mapping.*
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** A listening-derived event is not saved while its tier is ASK_FIRST. */
object PendingEventConfirmation {
    private const val CHANNEL = "shiv_event_confirmation"
    private const val ACTION_SAVE = "app.shiv.CONFIRM_EVENT_SAVE"

    suspend fun request(context: Context, event: EventRecord) {
        val dao = ShivDatabase.get(context.applicationContext).dao()
        dao.addPendingEvent(PendingEventRecord(id = event.id, title = event.title, startsAt = event.startsAt, place = event.place, person = event.person))
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Event confirmation", NotificationManager.IMPORTANCE_HIGH))
        val confirm = Intent(context, PendingEventReceiver::class.java).apply {
            action = ACTION_SAVE
            putExtra("eventId", event.id)
        }
        val action = PendingIntent.getBroadcast(context, event.id.hashCode(), confirm, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val date = android.text.format.DateFormat.format("EEE, MMM d · h:mm a", event.startsAt)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Shiv heard a possible commitment")
            .setContentText("${event.title} · $date")
            .addAction(0, "Confirm & save", action)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(event.id.hashCode(), notification) }
    }

    internal fun isSaveAction(action: String?) = action == ACTION_SAVE
}

class PendingEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!PendingEventConfirmation.isSaveAction(intent.action)) return
        val eventId = intent.getStringExtra("eventId") ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val dao = ShivDatabase.get(context.applicationContext).dao()
                val candidate = dao.pendingEvent(eventId) ?: return@launch
                if (candidate.title.length !in 2..120 || candidate.startsAt !in System.currentTimeMillis()..(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000L)) {
                    dao.deletePendingEvent(candidate.id)
                    return@launch
                }
                val event = EventRecord(id = candidate.id, title = candidate.title, startsAt = candidate.startsAt, place = candidate.place, person = candidate.person, source = "LISTENING", createdAt = candidate.createdAt)
                dao.saveEvent(event)
                dao.deletePendingEvent(candidate.id)
                val lead = dao.reminderRule(Brain.REMINDER_LEAD_KEY)?.value?.toIntOrNull() ?: Brain.DEFAULT_LEAD_MINUTES
                AlarmScheduler.schedule(context.applicationContext, event, lead)
                dao.logAction(ActionLogEntry(action = "Saved a listening event after confirmation", tier = ActionTier.ASK_FIRST.name, outcome = event.title))
            } finally { pending.finish() }
        }
    }
}
