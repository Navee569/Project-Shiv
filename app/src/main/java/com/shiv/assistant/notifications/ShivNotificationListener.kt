package com.shiv.assistant.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.shiv.assistant.automation.ShivSignalWorker
import com.shiv.assistant.data.ShivSignalQueue
import com.shiv.assistant.data.ShivVault
import com.shiv.assistant.domain.PrivacyPolicy

/**
 * Strict fail-closed capture. Denied app packages/OTP-like titles never reach storage,
 * logging, background work or an AI model.
 */
class ShivNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        try {
            val vault = ShivVault(applicationContext)
            if (vault.get("notification_opt_in") != "yes") return
            if (vault.get("sensing_paused") == "yes" || vault.get("guest_mode") == "yes" ||
                vault.get("privacy_zone") == "yes") return
            val extras = vault.get("denied_packages").orEmpty().lineSequence()
                .map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
            if (PrivacyPolicy.isDeniedPackage(sbn.packageName, extras)) return
            val raw = sbn.notification.extras.getCharSequence("android.title")?.toString()
            val title = PrivacyPolicy.safeNotificationTitle(sbn.packageName, raw, extras) ?: return
            val queue = ShivSignalQueue(vault)
            val id = queue.enqueue(sbn.packageName, title, extras) ?: return
            vault.addNotificationSummary(sbn.packageName, title)
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                "shiv_encrypted_signal_worker", ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<ShivSignalWorker>().build()
            )
        } catch (_: Exception) {
            // Never surface or log untrusted notification content.
        }
    }
}
