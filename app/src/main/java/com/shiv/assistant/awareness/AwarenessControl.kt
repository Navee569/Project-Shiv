package com.shiv.assistant.awareness

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** A visible, owner-controlled global pause for notification and screen awareness. */
object AwarenessControl {
    private const val PREFS="shiv_awareness_control"
    private const val CHANNEL="shiv_awareness_status"
    private const val ID=5161
    const val ACTION_PAUSE="com.shiv.assistant.action.PAUSE_AWARENESS"
    const val ACTION_RESUME="com.shiv.assistant.action.RESUME_AWARENESS"
    fun isPaused(context:Context):Boolean=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        .getBoolean("paused",false)
    fun canShow(context:Context):Boolean {
        if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=
            PackageManager.PERMISSION_GRANTED)return false
        val manager=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if(!manager.areNotificationsEnabled())return false
        return manager.getNotificationChannel(CHANNEL)?.importance!=NotificationManager.IMPORTANCE_NONE
    }
    fun mayCapture(context:Context):Boolean=!isPaused(context) && canShow(context)
    fun pause(context:Context) {
        check(context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putBoolean("paused",true).remove("capture_until").commit())
        show(context)
    }
    fun resume(context:Context) {
        check(context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putBoolean("paused",false).remove("capture_until").commit())
        // An earlier one-shot screen token is never restored by resume.
        show(context)
    }
    fun show(context:Context) {
        val manager=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        if(!prefs.getBoolean("notifications_enabled",false) &&
            !prefs.getBoolean("owner_enabled",false)) {
            manager.cancel(ID)
            return
        }
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"Shiv awareness control",
            NotificationManager.IMPORTANCE_LOW))
        if(!canShow(context))return
        val paused=isPaused(context)
        val action=if(paused) ACTION_RESUME else ACTION_PAUSE
        val label=if(paused) "RESUME" else "PAUSE"
        val intent=Intent(context,AwarenessControlReceiver::class.java).setAction(action)
        val pending=PendingIntent.getBroadcast(context,ID,intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notice=NotificationCompat.Builder(context,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle(if(paused)"Shiv awareness paused" else "Shiv awareness control")
            .setContentText(if(paused)"No notification or screen facts are captured"
                else "Tap PAUSE to stop notification and screen awareness")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause,label,pending)
            .build()
        manager.notify(ID,notice)
    }
}

class AwarenessControlReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent?) {
        when(intent?.action) {
            AwarenessControl.ACTION_PAUSE->AwarenessControl.pause(context)
            AwarenessControl.ACTION_RESUME->AwarenessControl.resume(context)
            Intent.ACTION_BOOT_COMPLETED->{
                // Boot never restores a screen inspection token.
                context.getSharedPreferences("shiv_awareness_control",Context.MODE_PRIVATE)
                    .edit().remove("capture_until").commit()
                AwarenessControl.show(context)
                val pending=goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try { com.shiv.assistant.automation.SignalWorker.recoverPending(context.applicationContext) }
                    finally { pending.finish() }
                }
            }
        }
    }
}
