package com.shiv.assistant.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.shiv.assistant.data.ShivRepository
import com.shiv.assistant.domain.SensitiveAppDenylist
import com.shiv.assistant.domain.SourceExclusions
import com.shiv.assistant.automation.SignalWorker
import kotlinx.coroutines.*
/**
 * Opt-in notification facts, never notification bodies. Denylisted apps are
 * rejected BEFORE reading notification extras, DB, logs, or any model call.
 */
class ShivNotificationListener:NotificationListenerService() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    override fun onNotificationPosted(sbn:StatusBarNotification?) {
        if(sbn==null || SourceExclusions.shouldBlock(applicationContext,sbn.packageName))return
        if(!getSharedPreferences("shiv_awareness_control",MODE_PRIVATE).getBoolean("notifications_enabled",false))return
        val title=sbn.notification.extras.getCharSequence("android.title")?.toString()?:return
        // Avoid OTPs, credential hints and personal raw message content.
        if(Regex("(?i)\\b(otp|password|pin|verification code|security code)\\b").containsMatchIn(title))return
        val fact=Regex("(?i)\\b(meeting|appointment|deadline|reminder|event|due)\\b")
            .find(title)?.value?:return
        scope.launch {
            val id=ShivRepository(applicationContext).incoming("notification",fact,priority=2,
                packageName=sbn.packageName)
            if(id!=null)SignalWorker.schedule(applicationContext)
        }
    }
    override fun onDestroy(){scope.cancel();super.onDestroy()}
}