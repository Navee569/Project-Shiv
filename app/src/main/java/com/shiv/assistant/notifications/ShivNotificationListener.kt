package com.shiv.assistant.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.shiv.assistant.data.ShivVault

/**
 * Optional notification metadata summaries. Only enabled when the user grants
 * Notification Access and switches on summaries in Shiv settings.
 */
class ShivNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        try {
            val vault = ShivVault(applicationContext)
            if (vault.get("notification_opt_in") != "yes") return
            val title = sbn.notification.extras.getCharSequence("android.title")?.toString() ?: return
            // Never persist notification bodies or read message content.
            vault.addNotificationSummary(sbn.packageName, title)
        } catch (_: Exception) {
            // Background notification delivery must never crash the app process.
        }
    }
}
