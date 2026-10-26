package com.shiv.assistant.automation

import com.shiv.assistant.data.ShivVault
import com.shiv.assistant.domain.ReminderRules
import java.time.LocalTime
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.UUID
import java.util.concurrent.TimeUnit

class ShivReminderWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val text = inputData.getString("text") ?: return Result.failure()
        // Apply the owner's *current* encrypted rule at delivery, not only at scheduling.
        val earliest = try { ShivVault(applicationContext).get("reminder_earliest_minute")?.toIntOrNull() }
            catch (_: Exception) { return Result.retry() } // fail closed on encrypted storage failure
        if (ReminderRules.shouldSuppress(LocalTime.now(), earliest)) return Result.success()
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel("shiv_reminders", "Shiv reminders", NotificationManager.IMPORTANCE_DEFAULT))
        if (Build.VERSION.SDK_INT >= 33 &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return Result.success() // notification permission is user-controlled
        manager.notify(text.hashCode(), NotificationCompat.Builder(applicationContext, "shiv_reminders")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Shiv reminder")
            .setContentText(text.take(240))
            .setAutoCancel(true).build())
        return Result.success()
    }
}
object ShivReminders {
    private val delay = Regex("(?i)\\bin\\s+(\\d+)\\s*(minutes?|hours?|days?)\\b")
    fun parseSeconds(command: String): Long? {
        val match = delay.find(command) ?: return null
        val n = match.groupValues[1].toLongOrNull() ?: return null
        if (n < 1 || n > 365) return null
        return when (match.groupValues[2].lowercase().first()) {
            'm' -> n * 60
            'h' -> n * 3600
            'd' -> n * 86400
            else -> null
        }
    }
    fun schedule(context: Context, text: String): UUID? {
        val secs = parseSeconds(text) ?: return null
        val task = OneTimeWorkRequestBuilder<ShivReminderWorker>()
            .setInputData(Data.Builder().putString("text", text.take(240)).build())
            .setInitialDelay(secs, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueue(task)
        return task.id
    }
}
