package app.shiv.platform

import android.content.Context
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.shiv.data.local.ShivDatabase
import app.shiv.data.mapping.RoomRecordMappers
import app.shiv.data.repository.RoomCaptureQueueRepository
import app.shiv.domain.actions.ActionAuditRepository
import app.shiv.domain.actions.ActionAuthorizationRequest
import app.shiv.domain.actions.ActionDispatchResult
import app.shiv.domain.actions.ActionExecutionStatus
import app.shiv.domain.actions.ActionPermissionState
import app.shiv.domain.actions.ActionTier
import app.shiv.domain.actions.AuthorizedActionExecutor
import app.shiv.domain.integration.CaptureQueueProcessor
import app.shiv.domain.integration.CaptureRunResult
import app.shiv.domain.model.CaptureSignal
import app.shiv.domain.model.EventRecord
import app.shiv.domain.model.PendingEventRecord
import app.shiv.domain.model.RecordSourceMetadata
import app.shiv.domain.model.ReminderRecord
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Processes encrypted inbox entries only after their Room transaction has committed. */
class CaptureQueueWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val database = ShivDatabase.get(applicationContext)
            val queue = RoomCaptureQueueRepository(database)
            val dao = database.dao()
            val brain = Brain(applicationContext)
            val processor = CaptureQueueProcessor(queue) { signal -> processSignal(signal, brain, database) }
            var processed = 0
            while (processed < MAX_BATCH_SIZE) {
                val run = processor.processNext()
                if (run.result == CaptureRunResult.NOT_FOUND) break
                if (run.result == CaptureRunResult.BUSY) break
                processed++
                dispatchPendingReminders(database)
            }
            // Also retry reminders committed by a prior run that stopped before alarm scheduling.
            dispatchPendingReminders(database)
            scheduleNextWake(applicationContext, queue.all())
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private suspend fun processSignal(
        signal: CaptureSignal,
        brain: Brain,
        database: ShivDatabase
    ): app.shiv.domain.repository.CaptureOutcome {
        val dao = database.dao()
        require(signal.source == SOURCE_LISTENING || signal.source == SOURCE_NOTIFICATION) {
            "Unsupported capture source"
        }
        val parsed = brain.extractEvent(signal.payload, source = signal.source)
            ?: return app.shiv.domain.repository.CaptureOutcome(
                sourceMetadata = RecordSourceMetadata(
                    recordType = "CAPTURE", recordId = signal.id, source = signal.source, capturedAt = signal.capturedAt
                )
            )
        val eventId = "capture-${signal.id}"
        val event = parsed.copy(id = eventId)
        val source = RecordSourceMetadata(
            recordType = "EVENT", recordId = event.id, source = signal.source, confidence = event.confidence,
            capturedAt = signal.capturedAt
        )
        val lead = dao.reminderRule(Brain.REMINDER_LEAD_KEY)?.value?.toIntOrNull() ?: Brain.DEFAULT_LEAD_MINUTES
        val reminder = ReminderRecord(
            id = "reminder-${signal.id}", eventId = event.id,
            triggerAt = event.startsAt - lead.coerceAtLeast(0) * 60_000L
        )
        val audit = AuthorizedActionExecutor(ActionAuditRepository { entry ->
            dao.logAction(RoomRecordMappers.toRoom(entry))
        })
        val report = audit.execute(
            ActionAuthorizationRequest(
                actionType = "CREATE_EVENT",
                arguments = mapOf("id" to event.id, "title" to event.title, "startsAt" to event.startsAt.toString()),
                configuredTier = dao.actionTier("CREATE_EVENT"),
                minimumTier = ActionTier.FREE,
                permissionState = ActionPermissionState.NOT_REQUIRED
            )
        ) {
            database.withTransaction {
                dao.saveEvent(RoomRecordMappers.toRoom(event))
                dao.saveReminder(RoomRecordMappers.toRoom(reminder))
                dao.deleteRecordSources("EVENT", event.id)
                dao.saveRecordSource(RoomRecordMappers.toRoom(source))
            }
            ActionDispatchResult("Event and reminder saved from ${signal.source}", "DELETE_EVENT:${event.id}")
        }
        return when (report.status) {
            ActionExecutionStatus.AWAITING_CONFIRMATION -> app.shiv.domain.repository.CaptureOutcome(
                pendingEvent = PendingEventRecord(
                    id = event.id, title = event.title, startsAt = event.startsAt, place = event.place,
                    person = event.person, createdAt = signal.capturedAt
                ),
                sourceMetadata = source
            )
            ActionExecutionStatus.SUCCEEDED -> app.shiv.domain.repository.CaptureOutcome(sourceMetadata = source)
            ActionExecutionStatus.AUDIT_UNAVAILABLE -> {
                if (!report.dispatched) error("Event action audit is unavailable")
                app.shiv.domain.repository.CaptureOutcome(sourceMetadata = source)
            }
            ActionExecutionStatus.FAILED -> error("Event action did not finish cleanly")
            else -> app.shiv.domain.repository.CaptureOutcome(sourceMetadata = source)
        }
    }

    private suspend fun dispatchPendingReminders(database: ShivDatabase) {
        val dao = database.dao()
        for (row in dao.pendingReminders(500)) {
            val eventRow = dao.eventById(row.eventId) ?: continue
            AlarmScheduler.scheduleAt(applicationContext, RoomRecordMappers.toDomain(eventRow), row.triggerAt)
            check(dao.setReminderState(row.id, "SCHEDULED", System.currentTimeMillis()) == 1)
        }
    }

    companion object {
        const val SOURCE_LISTENING = "LISTENING"
        const val SOURCE_NOTIFICATION = "NOTIFICATION"
        private const val WORK_NAME = "capture-queue"
        private const val WAKE_PREFIX = "capture-queue-wake-"
        private const val MAX_BATCH_SIZE = 20

        fun enqueue(context: Context, @Suppress("UNUSED_PARAMETER") captureId: String, notBeforeMillis: Long = System.currentTimeMillis()) {
            val delay = (notBeforeMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<CaptureQueueWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            if (delay == 0L) {
                WorkManager.getInstance(context.applicationContext)
                    .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
            } else {
                WorkManager.getInstance(context.applicationContext)
                    .enqueueUniqueWork(WAKE_PREFIX + notBeforeMillis, ExistingWorkPolicy.KEEP, request)
            }
        }

        suspend fun enqueuePending(context: Context) = withContext(Dispatchers.IO) {
            val repository = RoomCaptureQueueRepository(ShivDatabase.get(context.applicationContext))
            scheduleNextWake(context.applicationContext, repository.all())
        }

        private fun scheduleNextWake(context: Context, signals: List<CaptureSignal>) {
            val now = System.currentTimeMillis()
            val pending = signals.filter {
                it.status != app.shiv.domain.model.CaptureStatus.COMPLETED &&
                    it.status != app.shiv.domain.model.CaptureStatus.FAILED
            }
            if (pending.isEmpty()) return
            val due = pending.any { signal ->
                when (signal.status) {
                    app.shiv.domain.model.CaptureStatus.QUEUED,
                    app.shiv.domain.model.CaptureStatus.RETRY -> signal.nextAttemptAt <= now
                    app.shiv.domain.model.CaptureStatus.PROCESSING -> (signal.leaseUntil ?: 0L) <= now
                    app.shiv.domain.model.CaptureStatus.COMPLETED,
                    app.shiv.domain.model.CaptureStatus.FAILED -> false
                }
            }
            if (due) {
                enqueue(context, "queue-drain", now)
                return
            }
            val wakeAt = pending.mapNotNull { signal ->
                when (signal.status) {
                    app.shiv.domain.model.CaptureStatus.QUEUED,
                    app.shiv.domain.model.CaptureStatus.RETRY -> signal.nextAttemptAt
                    app.shiv.domain.model.CaptureStatus.PROCESSING -> signal.leaseUntil
                    app.shiv.domain.model.CaptureStatus.COMPLETED,
                    app.shiv.domain.model.CaptureStatus.FAILED -> null
                }
            }.minOrNull() ?: return
            enqueue(context, "queue-wake", wakeAt)
        }
    }
}
