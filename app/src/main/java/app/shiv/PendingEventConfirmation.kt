package app.shiv

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** A listening-derived event is not saved while its tier is ASK_FIRST. */
object PendingEventConfirmation {
    private const val CHANNEL = "shiv_event_confirmation"
    private const val ACTION_SAVE = "app.shiv.CONFIRM_EVENT_SAVE"
    private const val ACTION_DISMISS = "app.shiv.CONFIRM_EVENT_DISMISS"

    suspend fun request(context: Context, event: EventRecord, source: String = "LISTENING") {
        val dao = ShivDatabase.get(context.applicationContext).dao()
        dao.addPendingEvent(PendingEventRecord(id = event.id, title = event.title, startsAt = event.startsAt, place = event.place, person = event.person))
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Event confirmation", NotificationManager.IMPORTANCE_HIGH))
        val confirm = Intent(context, PendingEventReceiver::class.java).apply {
            action = ACTION_SAVE
            putExtra("eventId", event.id)
            putExtra("eventSource", source)
        }
        val action = PendingIntent.getBroadcast(context, event.id.hashCode(), confirm, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val dismissIntent = Intent(context, PendingEventReceiver::class.java).apply {
            action = ACTION_DISMISS
            putExtra("eventId", event.id)
        }
        val dismissAction = PendingIntent.getBroadcast(
            context, event.id.hashCode() xor Int.MIN_VALUE, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val date = android.text.format.DateFormat.format("EEE, MMM d · h:mm a", event.startsAt)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Shiv heard a possible commitment")
            .setContentText("${event.title} · $date")
            .addAction(0, "Confirm & save", action)
            .setDeleteIntent(dismissAction)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(event.id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission may be revoked after the explicit check.
        }
    }

    internal fun isSaveAction(action: String?) = action == ACTION_SAVE
    internal fun isDismissAction(action: String?) = action == ACTION_DISMISS
}

class PendingEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val save = PendingEventConfirmation.isSaveAction(intent.action)
        val dismiss = PendingEventConfirmation.isDismissAction(intent.action)
        if (!save && !dismiss) return
        val eventId = intent.getStringExtra("eventId") ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val dao = ShivDatabase.get(context.applicationContext).dao()
                if (dismiss) {
                    dao.deletePendingEvent(eventId)
                    NotificationManagerCompat.from(context).cancel(eventId.hashCode())
                    return@launch
                }
                val candidate = dao.pendingEvent(eventId) ?: return@launch
                if (!isPendingEventValid(candidate, System.currentTimeMillis())) {
                    dao.deletePendingEvent(candidate.id)
                    return@launch
                }
                val event = EventRecord(id = candidate.id, title = candidate.title, startsAt = candidate.startsAt, place = candidate.place, person = candidate.person, source = confirmedEventSource(intent.getStringExtra("eventSource")), createdAt = candidate.createdAt)
                dao.saveEvent(event)
                dao.deletePendingEvent(candidate.id)
                val lead = dao.reminderRule(Brain.REMINDER_LEAD_KEY)?.value?.toIntOrNull() ?: Brain.DEFAULT_LEAD_MINUTES
                AlarmScheduler.schedule(context.applicationContext, event, lead)
                dao.logAction(ActionLogEntry(action = "Saved a listening event after confirmation", tier = ActionTier.ASK_FIRST.name, outcome = event.title))
            } finally { pending.finish() }
        }
    }
}

internal fun confirmedEventSource(source: String?): String =
    if (source == "NOTIFICATION") "NOTIFICATION" else "LISTENING"

internal fun isPendingEventValid(candidate: PendingEventRecord, now: Long): Boolean =
    candidate.title.length in 2..120 &&
        candidate.startsAt in now..(now + 365L * 24 * 60 * 60 * 1000L)
