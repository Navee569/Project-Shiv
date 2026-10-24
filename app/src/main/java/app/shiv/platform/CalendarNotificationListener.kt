package app.shiv.platform

import app.shiv.data.local.*

import app.shiv.*

import app.shiv.data.repository.RoomCaptureQueueRepository
import app.shiv.domain.integration.CaptureSignalIntake
import app.shiv.domain.model.CapturePriority
import app.shiv.domain.model.CaptureSignal
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Optional user-enabled listener. Notification text is encrypted before any Brain processing. */
open class CalendarNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val transientText = "$title $text".trim()
        if (transientText.isBlank()) return
        scope.launch {
            val signal = CaptureSignal(
                payload = transientText,
                source = CaptureQueueWorker.SOURCE_NOTIFICATION,
                capturedAt = sbn.postTime,
                priority = CapturePriority.NORMAL
            )
            CaptureSignalIntake(RoomCaptureQueueRepository(ShivDatabase.get(applicationContext))) { queued ->
                CaptureQueueWorker.enqueue(applicationContext, queued.id)
            }.receive(signal)
        }
    }
}
