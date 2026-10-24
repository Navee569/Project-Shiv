package app.shiv

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import app.shiv.domain.scheduling.ProactiveSchedulePolicy

internal interface ProactiveNotifications {
    fun allowed(): Boolean
    /** False means definitely not delivered; a thrown failure has an uncertain delivery outcome. */
    fun post(eventId: String, title: String, body: String): Boolean
}

internal data class ProactiveDependencies(
    val database: ShivDatabase,
    val brain: ProactiveBrain,
    val notifications: ProactiveNotifications,
    val now: () -> Long = System::currentTimeMillis
)

/** Periodically asks the on-device Brain whether an imminent event deserves a nudge. */
class ProactiveWorker internal constructor(
    context: Context,
    params: WorkerParameters,
    private val injected: ProactiveDependencies?
) : CoroutineWorker(context, params) {
    constructor(context: Context, params: WorkerParameters) : this(context, params, null)

    override suspend fun doWork(): Result {
        val dependencies = injected ?: ProactiveDependencies(
            ShivDatabase.get(applicationContext), BrainProvider.get(applicationContext),
            AndroidProactiveNotifications(applicationContext)
        )
        val database = dependencies.database
        val dao = database.dao()
        val initialNow = dependencies.now()
        val settings = dao.settingsOnce() ?: ShivSettings()
        if (!eligibleSettings(settings, initialNow) || !dependencies.notifications.allowed()) return Result.success()
        if (dao.countProactiveSince(startOfDay(initialNow)) >= settings.maxDailyInterruptions) return Result.success()

        // SQL excludes both delivered and reserved events before applying the context limit.
        val events = dao.proactiveEvents(initialNow, initialNow + TimeUnit.HOURS.toMillis(48))
        if (events.isEmpty()) return Result.success()
        val contextSnapshot = buildString {
            appendLine("Current time: $initialNow")
            appendLine("Upcoming events in the next 48 hours:")
            events.forEach { event ->
                appendLine("- id=${event.id}; ${event.title}; startsAt=${event.startsAt}; person=${event.person.orEmpty()}; place=${event.place.orEmpty()}")
            }
            appendLine("Choose one listed eventId for a useful notification, or stay_quiet.")
        }
        val decision = dependencies.brain.decideProactive(contextSnapshot) ?: return Result.success()
        if (decision.tool != "send_notification") return Result.success()
        val selected = events.firstOrNull { it.id == decision.eventId } ?: return Result.success()
        val title = decision.notificationTitle?.trim()?.takeIf { it.isNotEmpty() && it.length <= 80 } ?: return Result.success()
        val body = decision.notificationBody?.trim()?.takeIf { it.isNotEmpty() && it.length <= 220 } ?: return Result.success()

        // Commit the reservation BEFORE touching Android. Room serializes the cap/dedupe checks
        // with this write across immediate and periodic workers. A crash cannot free a used slot.
        val reservation = database.withTransaction {
            val now = dependencies.now()
            val current = dao.settingsOnce() ?: ShivSettings()
            if (!eligibleSettings(current, now) || !dependencies.notifications.allowed() ||
                !eligibleEvent(selected, dao.eventById(selected.id), now) ||
                dao.wasProactiveSent(selected.id) ||
                dao.countProactiveSince(startOfDay(now)) >= current.maxDailyInterruptions) return@withTransaction null
            val tier = proactiveNotificationTier(dao.actionTier("SEND_NOTIFICATION")) ?: return@withTransaction null
            ActionLogEntry(action = RESERVED_ACTION, tier = tier.name, outcome = selected.id, createdAt = now)
                .also { dao.logAction(it) }
        } ?: return Result.success()

        try {
            // Re-read after reservation as well: settings/event edits can commit between the two
            // transactions. Keep the final validation, dispatch, and accounting serialized with
            // other workers and DB writers. Full event equality invalidates an edited snapshot.
            database.withTransaction {
                val current = dao.settingsOnce() ?: ShivSettings()
                val tier = proactiveNotificationTier(dao.actionTier("SEND_NOTIFICATION"))
                val reserved = dao.actionById(reservation.id)
                val currentEvent = dao.eventById(selected.id)
                val interruptions = dao.countProactiveSince(startOfDay(reservation.createdAt))
                // Sample time after the suspended DB reads; the valid path dispatches immediately.
                val now = dependencies.now()
                if (reserved != reservation || !eligibleSettings(current, now) ||
                    !dependencies.notifications.allowed() || tier == null ||
                    !eligibleEvent(selected, currentEvent, now) ||
                    startOfDay(now) != startOfDay(reservation.createdAt) ||
                    interruptions > current.maxDailyInterruptions) {
                    dao.deleteAction(reservation.id)
                    return@withTransaction
                }
                if (dependencies.notifications.post(selected.id, title, body)) {
                    dao.logAction(reservation.copy(action = SENT_ACTION, tier = tier.name, createdAt = now))
                } else {
                    dao.deleteAction(reservation.id)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Dispatch/commit may have succeeded before failure was reported. Retain the durable
            // reservation and fail closed rather than risk an unaccounted duplicate on retry.
        }
        return Result.success()
    }

    companion object {
        private const val PERIODIC_WORK = "shiv_proactive_periodic"
        private const val CHECK_WORK = "shiv_proactive_check"
        internal const val RESERVED_ACTION = "Proactive notification reserved"
        internal const val SENT_ACTION = "Proactive notification"

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

private class AndroidProactiveNotifications(private val context: Context) : ProactiveNotifications {
    override fun allowed(): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    override fun post(eventId: String, title: String, body: String): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        if (!allowed()) return false
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel("shiv_proactive", "Shiv proactive reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val notification = NotificationCompat.Builder(context, "shiv_proactive")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(body).setAutoCancel(true).build()
        manager.notify(eventId, 0, notification)
        return true
    }
}

private fun eligibleSettings(settings: ShivSettings, now: Long): Boolean {
    val calendar = Calendar.getInstance().apply { timeInMillis = now }
    val minute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    return ProactiveSchedulePolicy.isEligible(
        settings.proactiveEnabled,
        settings.maxDailyInterruptions,
        minute,
        settings.quietStartMinute,
        settings.quietEndMinute
    )
}

private fun eligibleEvent(snapshot: EventRecord, current: EventRecord?, now: Long): Boolean =
    snapshot == current && snapshot.startsAt > now && snapshot.startsAt <= now + TimeUnit.HOURS.toMillis(48)

private fun startOfDay(now: Long): Long = Calendar.getInstance().apply {
    timeInMillis = now
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

internal fun proactiveNotificationTier(configuredTier: String?): ActionTier? {
    val configured = configuredTier
        ?.let { runCatching { ActionTier.valueOf(it) }.getOrNull() }
        ?: ActionTier.ACT_THEN_TELL
    val effective = if (configured.ordinal < ActionTier.ACT_THEN_TELL.ordinal) ActionTier.ACT_THEN_TELL else configured
    return effective.takeUnless { it == ActionTier.ASK_FIRST }
}
