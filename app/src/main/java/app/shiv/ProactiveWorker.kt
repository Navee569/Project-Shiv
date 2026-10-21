package app.shiv

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Delivers a quiet-hours-aware, rate-limited nudge for an upcoming saved event. */
class ProactiveWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return Result.success()

        val dao = ShivDatabase.get(context).dao()
        val settings = dao.settingsOnce() ?: ShivSettings().also { dao.saveSettings(it) }
        if (!settings.proactiveEnabled || settings.maxDailyInterruptions <= 0) {
            return Result.success()
        }

        val now = Calendar.getInstance()
        val minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        if (isQuietHours(minuteOfDay, settings.quietStartMinute, settings.quietEndMinute)) {
            return Result.success()
        }

        val dayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        if (dao.countActionsSince("Proactive notification", dayStart) >=
            settings.maxDailyInterruptions
        ) {
            return Result.success()
        }

        val nowMillis = now.timeInMillis
        val event = dao.upcomingEvents(nowMillis).firstOrNull {
            it.startsAt <= nowMillis + NUDGE_WINDOW_MILLIS &&
                !dao.wasProactiveSent(it.id)
        } ?: return Result.success()

        val minutesUntil = ((event.startsAt - nowMillis + 59_999L) / 60_000L)
            .coerceAtLeast(1L)
        val details = buildString {
            append("Starts in ")
            append(minutesUntil)
            append(if (minutesUntil == 1L) " minute" else " minutes")
            event.place?.takeIf(String::isNotBlank)?.let {
                append(" · ")
                append(it)
            }
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Shiv proactive reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(event.title.ifBlank { "Upcoming event" })
            .setContentText(details)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setAutoCancel(true)
            .build()

        return try {
            notifications.notify(event.id.hashCode(), notification)
            dao.logAction(
                ActionLogEntry(
                    action = "Proactive notification",
                    tier = ActionTier.FREE.name,
                    outcome = event.id
                )
            )
            Result.success()
        } catch (_: SecurityException) {
            // Notification permission may be revoked between the permission check and notify().
            Result.success()
        }
    }

    private fun isQuietHours(minute: Int, start: Int, end: Int): Boolean {
        if (start == end) return true
        return if (start < end) minute in start until end
        else minute >= start || minute < end
    }

    companion object {
        private const val UNIQUE_PERIODIC_WORK = "shiv_proactive_periodic"
        private const val UNIQUE_ONE_TIME_WORK = "shiv_proactive_check_soon"
        private const val CHANNEL_ID = "shiv_proactive"
        private const val NUDGE_WINDOW_MILLIS = 30 * 60 * 1000L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ProactiveWorker>(
                15,
                TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun checkSoon(context: Context) {
            val request = OneTimeWorkRequestBuilder<ProactiveWorker>()
                .setInitialDelay(1, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_ONE_TIME_WORK,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
