package com.shiv.assistant.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.shiv.assistant.data.ShivRepository
import com.shiv.assistant.domain.SensitiveAppDenylist
import com.shiv.assistant.domain.AppSourceAccess
import com.shiv.assistant.automation.SignalWorker
import kotlinx.coroutines.*

/**
 * Opt-in scheduling keyword only. No notification bodies or raw titles saved.
 * Denied packages are rejected BEFORE examining notification extras.
 * Encrypted custom allow/deny checks run on IO, never block listener callbacks.
 */
class ShivNotificationListener:NotificationListenerService() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    override fun onNotificationPosted(sbn:StatusBarNotification?) {
        if(sbn==null || SensitiveAppDenylist.excluded(sbn.packageName))return
        if(!getSharedPreferences("shiv_awareness_control",MODE_PRIVATE)
                .getBoolean("notifications_enabled",false))return
        scope.launch {
            if(!getSharedPreferences("shiv_awareness_control",MODE_PRIVATE)
                    .getBoolean("notifications_enabled",false))return@launch
            if(!AppSourceAccess.mayRead(applicationContext,sbn.packageName,"notification"))
                return@launch
            val title=sbn.notification.extras.getCharSequence("android.title")?.toString()
                ?:return@launch
            if(Regex("(?i)\\b(otp|password|pin|verification code|security code)\\b")
                    .containsMatchIn(title))return@launch
            val fact=Regex("(?i)\\b(meeting|appointment|deadline|reminder|event|due)\\b")
                .find(title)?.value?:return@launch
            val id=ShivRepository(applicationContext).incoming(
                "notification",fact,priority=2,packageName=sbn.packageName)
            if(id!=null)SignalWorker.schedule(applicationContext)
        }
    }
    override fun onDestroy(){scope.cancel();super.onDestroy()}
}
