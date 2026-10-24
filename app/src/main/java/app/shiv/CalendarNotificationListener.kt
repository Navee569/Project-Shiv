package app.shiv

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Optional user-enabled listener. Notification text is inspected in memory and never stored. */
class CalendarNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val eventMutex = Mutex()
    private val brain by lazy { BrainProvider.get(applicationContext) }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val transientText = "$title $text".trim()
        if (transientText.isBlank()) return
        val event = brain.extractEvent(transientText, source = "NOTIFICATION") ?: return
        scope.launch {
            eventMutex.withLock {
            val dao = ShivDatabase.get(applicationContext).dao()
            val tier = ActionTierPolicy.resolve(dao.actionTier("CREATE_EVENT"))
            val sink = object : NotificationEventSink {
                override suspend fun isDuplicate(candidate: EventRecord): Boolean =
                    dao.hasEventForTitleAndStart(candidate.title, candidate.startsAt) ||
                        dao.hasPendingEventForTitleAndStart(candidate.title, candidate.startsAt)

                override suspend fun createPendingConfirmation(candidate: EventRecord) {
                    PendingEventConfirmation.request(applicationContext, candidate, source = "NOTIFICATION")
                }

                override suspend fun saveEvent(candidate: EventRecord) {
                    dao.saveEvent(candidate)
                    dao.logAction(ActionLogEntry(action = "Saved an event from a notification", tier = tier.name, outcome = candidate.title))
                }

                override suspend fun scheduleReminder(candidate: EventRecord) {
                    val lead = dao.reminderRule(Brain.REMINDER_LEAD_KEY)?.value?.toIntOrNull() ?: Brain.DEFAULT_LEAD_MINUTES
                    AlarmScheduler.schedule(applicationContext, candidate, lead)
                }

                override suspend fun notifySavedEvent(candidate: EventRecord) {
                    this@CalendarNotificationListener.notifySavedEvent(candidate)
                }
            }
            orchestrateNotificationEvent(event, tier, sink)
            }
        }
        // The notification is only a transient input; only a parsed event is persisted.
    }
}


private fun CalendarNotificationListener.notifySavedEvent(event: EventRecord) {
    if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = "shiv_event_saved"
    if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(channel, "Saved events", NotificationManager.IMPORTANCE_DEFAULT))
    val date = android.text.format.DateFormat.format("EEE, MMM d · h:mm a", event.startsAt)
    val notice = NotificationCompat.Builder(this, channel)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Shiv saved an event")
        .setContentText(event.title + " · " + date)
        .setAutoCancel(true)
        .build()
    runCatching { NotificationManagerCompat.from(this).notify(event.id.hashCode(), notice) }
}
