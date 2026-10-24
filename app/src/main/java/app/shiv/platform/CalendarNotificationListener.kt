package app.shiv.platform

import app.shiv.data.local.*

import app.shiv.*

import app.shiv.data.mapping.*
import app.shiv.domain.actions.ActionTier
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Optional user-enabled listener. Notification text is inspected in memory and never stored. */
open class CalendarNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val brain = Brain()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val transientText = "$title $text".trim()
        if (transientText.isBlank()) return
        val event = brain.extractEvent(transientText, source = "NOTIFICATION") ?: return
        scope.launch {
            val dao = ShivDatabase.get(applicationContext).dao()
            dao.saveEvent(event)
            dao.logAction(ActionLogEntry(action = "Saved an event from a notification", tier = ActionTier.FREE.name, outcome = event.title))
            val lead = dao.reminderRule(Brain.REMINDER_LEAD_KEY)?.value?.toIntOrNull() ?: Brain.DEFAULT_LEAD_MINUTES
            AlarmScheduler.schedule(applicationContext, event, lead)
        }
        // The notification is only a transient input; only a parsed event is persisted.
    }
}
