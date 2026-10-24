package app.shiv.platform

import app.shiv.*

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) return Result.success()
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel("shiv_reminders", "Shiv reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val notification = NotificationCompat.Builder(applicationContext, "shiv_reminders").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Shiv reminder").setContentText(inputData.getString("title") ?: "You asked me to remind you").setAutoCancel(true).build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
        return Result.success()
    }
}
