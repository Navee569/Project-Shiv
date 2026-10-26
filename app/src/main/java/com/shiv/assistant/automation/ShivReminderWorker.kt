package com.shiv.assistant.automation

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.shiv.assistant.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.*
import java.util.UUID
import java.util.concurrent.TimeUnit

/** WorkManager stores ONLY an opaque UUID; reminder text lives in SQLCipher. */
class ShivReminderWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    override suspend fun doWork():Result=withContext(Dispatchers.IO) {
        val id=inputData.getString("reminder_id")?:return@withContext Result.failure()
        val dao=EncryptedDatabase.open(applicationContext).dao()
        val reminder=dao.reminder(id)?:return@withContext Result.failure()
        if(reminder.status !in setOf("SCHEDULED","DEFERRED"))return@withContext Result.success()
        val before=dao.rules().firstOrNull{it.key=="reminders_minute_of_day"}?.value?.toIntOrNull()
        val now=ZonedDateTime.now()
        if(before!=null && (now.hour*60+now.minute)<before && before in 0..1439) {
            val deferUntil=now.toLocalDate().atStartOfDay(now.zone).plusMinutes(before.toLong())
            val seconds=java.time.Duration.between(now,deferUntil).seconds.coerceAtLeast(30L)
            dao.reminderStatus(id,"DEFERRED")
            ShivReminders.enqueueId(applicationContext,id,seconds)
            return@withContext Result.success()
        }
        val manager=applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel("shiv_reminders","Shiv reminders",
            NotificationManager.IMPORTANCE_DEFAULT))
        if(Build.VERSION.SDK_INT>=33 &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            dao.reminderStatus(id,"NOTIFICATION_BLOCKED")
            return@withContext Result.success()
        }
        manager.notify(id.hashCode(),NotificationCompat.Builder(applicationContext,"shiv_reminders")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Shiv reminder")
            .setContentText(reminder.label.take(240)).setAutoCancel(true).build())
        dao.reminderStatus(id,"SHOWN")
        Result.success()
    }
}
object ShivReminders {
    private val delay=Regex("(?i)\\bin\\s+(\\d+)\\s*(minutes?|hours?|days?)\\b")
    fun parseSeconds(command:String):Long? {
        val match=delay.find(command)?:return null
        val n=match.groupValues[1].toLongOrNull()?:return null
        if(n<1 || n>365)return null
        return when(match.groupValues[2].lowercase().first()) {
            'm'->n*60
            'h'->n*3600
            'd'->n*86400
            else->null
        }
    }
    fun enqueueId(context:Context,id:String,delaySeconds:Long) {
        val task=OneTimeWorkRequestBuilder<ShivReminderWorker>()
            .setInputData(Data.Builder().putString("reminder_id",id).build())
            .addTag("shiv-reminder-"+id)
            .setInitialDelay(delaySeconds,TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueue(task)
    }
    fun scheduleApprovedEvent(context:Context,reminderId:String,dueAtMillis:Long) {
        val delay=((dueAtMillis-System.currentTimeMillis())/1000L).coerceAtLeast(1L)
        val task=OneTimeWorkRequestBuilder<ShivReminderWorker>()
            .setInputData(Data.Builder().putString("reminder_id",reminderId).build())
            .addTag("shiv-reminder-"+reminderId)
            .setInitialDelay(delay,TimeUnit.SECONDS).build()
        // Keep is idempotent for repeated approval or a retried command.
        WorkManager.getInstance(context).enqueueUniqueWork(
            "shiv-approved-"+reminderId,ExistingWorkPolicy.KEEP,task)
    }
    /** Returns whether a still-pending reminder was marked canceled; already shown
     * notifications cannot be recalled. The DB status is checked at delivery. */
    suspend fun undo(context:Context,id:String):Boolean = withContext(Dispatchers.IO) {
        val dao=EncryptedDatabase.open(context).dao()
        val record=dao.reminder(id) ?: return@withContext false
        if(record.status !in setOf("SCHEDULED","DEFERRED"))return@withContext false
        dao.reminderStatus(id,"CANCELLED")
        WorkManager.getInstance(context).cancelAllWorkByTag("shiv-reminder-"+id)
        true
    }
    fun schedule(context:Context,text:String):UUID? {
        val secs=parseSeconds(text)?:return null
        val id=UUID.randomUUID()
        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            EncryptedDatabase.open(context).dao().putReminder(
                ReminderRecord(id.toString(),text.take(240),System.currentTimeMillis()+secs*1000,null))
        }
        enqueueId(context,id.toString(),secs)
        return id
    }
}