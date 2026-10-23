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

/** Periodically asks the on-device Brain whether an imminent event deserves a nudge. */
class ProactiveWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val dao = ShivDatabase.get(context).dao()
        val settings = dao.settingsOnce() ?: ShivSettings().also { dao.saveSettings(it) }
        if (!settings.proactiveEnabled || settings.maxDailyInterruptions <= 0) return Result.success()

        val now = Calendar.getInstance()
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val quiet = isQuietMinute(minute, settings.quietStartMinute, settings.quietEndMinute)

        if (quiet || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return Result.success()

        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        if (dao.countActionsSince("Proactive notification", startOfDay) >= settings.maxDailyInterruptions) return Result.success()

        val horizon = now.timeInMillis + TimeUnit.HOURS.toMillis(48)
        val events = dao.upcomingEvents(now.timeInMillis)
            .filter { it.startsAt <= horizon }
            .take(8)
        if (events.isEmpty() || events.all { dao.wasProactiveSent(it.id) }) return Result.success()

        val contextSnapshot = buildString {
            appendLine("Current time: ${now.timeInMillis}")
            appendLine("Upcoming events in the next 48 hours:")
            events.filterNot { dao.wasProactiveSent(it.id) }.forEach { event ->
                appendLine("- id=${event.id}; ${event.title}; startsAt=${event.startsAt}; person=${event.person.orEmpty()}; place=${event.place.orEmpty()}")
            }
            appendLine("The owner has enabled proactive notifications. Decide whether one timely, useful notification is justified.")
        }
        val decision = BrainProvider.get(context).decideProactive(contextSnapshot) ?: return Result.success()
        if (decision.tool != "send_notification") return Result.success()

        val title = decision.notificationTitle?.trim()?.take(80)?.takeIf(String::isNotEmpty) ?: return Result.success()
        val body = decision.notificationBody?.trim()?.take(240)?.takeIf(String::isNotEmpty) ?: return Result.success()
        val event = events.firstOrNull { !dao.wasProactiveSent(it.id) } ?: return Result.success()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Shiv proactive reminders", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        val tier = proactiveNotificationTier(dao.actionTier("SEND_NOTIFICATION")) ?: return Result.success()

        if (runCatching { manager.notify(event.id.hashCode(), notification) }.isFailure) return Result.success()
        dao.logAction(ActionLogEntry(action = "Proactive notification", tier = tier.name, outcome = event.id))
        return Result.success()
    }

    companion object {
        private const val PERIODIC_WORK = "shiv_proactive_periodic"
        private const val CHECK_WORK = "shiv_proactive_check"
        private const val CHANNEL_ID = "shiv_proactive"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ProactiveWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun checkSoon(context: Context) {
            val request = OneTimeWorkRequestBuilder<ProactiveWorker>().build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(CHECK_WORK, ExistingWorkPolicy.KEEP, request)
        }
    }
}

internal fun isQuietMinute(minute: Int, startMinute: Int, endMinute: Int): Boolean {
    val safeMinute = minute.coerceIn(0, 1439)
    val start = startMinute.coerceIn(0, 1439)
    val end = endMinute.coerceIn(0, 1439)
    return when {
        start == end -> true
        start < end -> safeMinute in start until end
        else -> safeMinute >= start || safeMinute < end
    }
}

internal fun proactiveNotificationTier(configuredTier: String?): ActionTier? {
    val configured = configuredTier
        ?.let { runCatching { ActionTier.valueOf(it) }.getOrNull() }
        ?: ActionTier.ACT_THEN_TELL
    val effective = if (configured.ordinal < ActionTier.ACT_THEN_TELL.ordinal) ActionTier.ACT_THEN_TELL else configured
    return effective.takeUnless { it == ActionTier.ASK_FIRST }
}
