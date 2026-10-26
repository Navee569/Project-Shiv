package com.shiv.assistant.automation

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shiv.assistant.data.EncryptedDatabase
import com.shiv.assistant.data.OwnerNudgeLedger
import com.shiv.assistant.data.ShivVault
import com.shiv.assistant.domain.AgendaNudge
import com.shiv.assistant.domain.AgendaNudgePolicy
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Optional, best-effort proactive agenda notices. The only source is the
 * owner's existing encrypted events; no neural model, remote API, background
 * microphone or third-party content is read. Quiet hours and daily cap apply.
 */
class ShivAgendaNudgeWorker(context:Context, params:WorkerParameters):
    CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        return try {
            val context=applicationContext
            val vault=ShivVault(context)
            // Fail closed after owner opt-out, privacy pause or an owner-data wipe.
            if(vault.get("proactive_opt_in")!="yes")return Result.success()
            val control=context.getSharedPreferences("shiv_awareness_control",Context.MODE_PRIVATE)
            if(control.getBoolean("paused",false))return Result.success()
            if(Build.VERSION.SDK_INT>=33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=
                PackageManager.PERMISSION_GRANTED) return Result.success()
            val now=System.currentTimeMillis()
            val ledger=OwnerNudgeLedger(vault)
            val (delivered,count)=ledger.deliveredToday(now)
            val dailyLimit=ledger.suggestedDailyLimit()
            if(count>=dailyLimit)return Result.success()
            val dao=EncryptedDatabase.open(context).dao()
            val imminent=dao.events(now,now+90*60_000L)
            fun stableKey(eventId:String):String {
                val digest=MessageDigest.getInstance("SHA-256")
                    .digest(eventId.toByteArray(Charsets.UTF_8))
                return "agenda:"+digest.take(12).joinToString(""){"%02x".format(it.toInt() and 0xff)}
            }
            val event=imminent.firstOrNull {
                AgendaNudgePolicy.decide(AgendaNudge(stableKey(it.id),it.startAt),
                    now,delivered,count,dailyLimit).deliver
            }
            val selectedKey:String
            val title:String
            val text:String
            if(event!=null) {
                selectedKey=stableKey(event.id)
                title="Shiv • Upcoming saved event"
                text="You have an approved event within 90 minutes. Open Shiv to review."
            } else {
                val hour=ZonedDateTime.now().hour
                val kind=when(hour) { in 9..10->"morning";in 18..20->"evening";else->null }
                    ?:return Result.success()
                val withinNextDay=dao.events(now,now+24*3_600_000L)
                if(withinNextDay.isEmpty())return Result.success()
                selectedKey="brief:"+kind
                val brief=AgendaNudge(selectedKey,now)
                if(!AgendaNudgePolicy.decide(brief,now,delivered,count,dailyLimit).deliver)
                    return Result.success()
                title=if(kind=="morning")"Shiv • Your saved agenda" else "Shiv • Evening agenda"
                text=withinNextDay.size.toString()+" upcoming item(s) in your encrypted Shiv agenda."
            }
            if(vault.get("proactive_opt_in")!="yes" ||
                control.getBoolean("paused",false))return Result.success()
            if(!ledger.reserveNotification(selectedKey,now))return Result.success()
            val manager=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel("shiv_optional_agenda",
                "Shiv optional agenda notices",NotificationManager.IMPORTANCE_DEFAULT))
            manager.notify(selectedKey.hashCode(),
                NotificationCompat.Builder(context,"shiv_optional_agenda")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title).setContentText(text)
                    .setAutoCancel(true).build())
            Result.success()
        } catch (_:Exception) {
            // No private database details or event titles enter an Android log.
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME="shiv-opt-in-agenda-nudges"
        fun updateRegistration(context:Context, enabled:Boolean) {
            val manager=WorkManager.getInstance(context)
            if(!enabled) {
                manager.cancelUniqueWork(UNIQUE_NAME)
                return
            }
            manager.enqueueUniquePeriodicWork(UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ShivAgendaNudgeWorker>(1,TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder()
                        .setRequiresBatteryNotLow(true).build()).build())
        }
    }
}
