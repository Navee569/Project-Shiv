package com.shiv.assistant.integrations

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Data
import com.shiv.assistant.automation.SignalWorker
import com.shiv.assistant.data.ShivRepository
import com.shiv.assistant.data.ShivVault
import kotlinx.coroutines.*
/**
 * Optional call-ended metadata only. No audio, recordings, contact number or call-log text.
 * The owner can disable this before requesting the READ_PHONE_STATE permission.
 */
class PostCallCheckIn:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent?) {
        if(intent?.action!=TelephonyManager.ACTION_PHONE_STATE_CHANGED)return
        if(context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED)return
        val pref=context.getSharedPreferences("shiv_call_check_in",Context.MODE_PRIVATE)
        if(!pref.getBoolean("owner_enabled",false))return
        val state=intent.getStringExtra(TelephonyManager.EXTRA_STATE)?:return
        if(state==TelephonyManager.EXTRA_STATE_OFFHOOK) {
            pref.edit().putBoolean("in_call",true).apply()
        } else if(state==TelephonyManager.EXTRA_STATE_IDLE && pref.getBoolean("in_call",false)) {
            pref.edit().putBoolean("in_call",false).apply()
            val job=goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val id=ShivRepository(context.applicationContext).incoming(
                        "call-end","Call ended; ask owner whether anything was agreed",7)
                    if(id!=null) {
                        ShivVault(context.applicationContext).put("pending_post_call_signal",id)
                        SignalWorker.schedule(context.applicationContext)
                        postCheckIn(context)
                    }
                } finally {job.finish()}
            }
        }
    }
    private fun postCheckIn(context:Context) {
        val allowed=android.os.Build.VERSION.SDK_INT<33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
        if(!allowed)return
        val nm=context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.createNotificationChannel(android.app.NotificationChannel("shiv_check_in",
            "Shiv post-call check-ins",android.app.NotificationManager.IMPORTANCE_DEFAULT))
        val intent=Intent(context,com.shiv.assistant.MainActivity::class.java)
        val pi=android.app.PendingIntent.getActivity(context,10,intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        val notification=androidx.core.app.NotificationCompat.Builder(context,"shiv_check_in")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Shiv — post-call check-in")
            .setContentText("Was anything agreed during that call? Open Shiv to tell him.")
            .setContentIntent(pi).setAutoCancel(true).build()
        nm.notify(2055,notification)
    }
}
