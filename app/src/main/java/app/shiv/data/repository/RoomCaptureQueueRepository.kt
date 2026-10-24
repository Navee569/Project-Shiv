package app.shiv.data.repository

import app.shiv.data.local.ShivDatabase
import app.shiv.data.mapping.RoomRecordMappers
import app.shiv.domain.model.CaptureSignal
import app.shiv.domain.repository.CaptureLease
import app.shiv.domain.repository.CaptureOutcome
import app.shiv.domain.repository.CaptureQueueRepository
import androidx.room.withTransaction
import java.util.UUID

class RoomCaptureQueueRepository(private val database: ShivDatabase) : CaptureQueueRepository {
    private val dao = database.dao()

    override suspend fun enqueue(signal: CaptureSignal): Boolean {
        require(signal.source.isNotBlank())
        require(signal.payload.isNotBlank())
        require(signal.status == app.shiv.domain.model.CaptureStatus.QUEUED && signal.attempts == 0)
        return dao.enqueueCapture(RoomRecordMappers.toRoom(signal)) != -1L
    }

    override suspend fun get(id: String): CaptureSignal? = dao.capture(id)?.let(RoomRecordMappers::toDomain)

    override suspend fun all(): List<CaptureSignal> = dao.allCaptures().map(RoomRecordMappers::toDomain)

    override suspend fun claim(id: String, now: Long, leaseMillis: Long): CaptureLease? {
        require(leaseMillis > 0)
        return database.withTransaction { claimLocked(id, now, leaseMillis) }
    }

    override suspend fun claimNext(now: Long, leaseMillis: Long): CaptureLease? {
        require(leaseMillis > 0)
        return database.withTransaction {
            val candidate = dao.nextCapture(now) ?: return@withTransaction null
            claimLocked(candidate.id, now, leaseMillis)
        }
    }

    private suspend fun claimLocked(id: String, now: Long, leaseMillis: Long): CaptureLease? {
        val token = UUID.randomUUID().toString()
        if (dao.claimCapture(id, token, now, now + leaseMillis) != 1) return null
        val claimed = dao.capture(id) ?: return null
        return CaptureLease(RoomRecordMappers.toDomain(claimed), token)
    }

    override suspend fun complete(lease: CaptureLease, outcome: CaptureOutcome, completedAt: Long): Boolean = database.withTransaction {
        val stored = dao.capture(lease.signal.id)
        if (stored?.status != "PROCESSING" || stored.leaseToken != lease.token) return@withTransaction false
        outcome.event?.let { dao.saveEvent(RoomRecordMappers.toRoom(it)) }
        outcome.pendingEvent?.let { dao.addPendingEvent(RoomRecordMappers.toRoom(it)) }
        outcome.reminder?.let { dao.saveReminder(RoomRecordMappers.toRoom(it)) }
        outcome.sourceMetadata?.let { metadata ->
            dao.deleteRecordSources(metadata.recordType, metadata.recordId)
            dao.saveRecordSource(RoomRecordMappers.toRoom(metadata))
        }
        check(dao.completeCapture(lease.signal.id, lease.token, completedAt) == 1) {
            "Capture lease changed while committing its result"
        }
        true
    }

    override suspend fun retry(lease: CaptureLease, nextAttemptAt: Long, failureCode: String): Boolean =
        dao.retryCapture(lease.signal.id, lease.token, nextAttemptAt, safeCode(failureCode)) == 1

    override suspend fun markFailed(lease: CaptureLease, failureCode: String): Boolean =
        dao.failCapture(lease.signal.id, lease.token, safeCode(failureCode)) == 1

    override suspend fun clear() = dao.clearCaptures()

    private fun safeCode(value: String): String = when (value) {
        "PROCESSING_FAILED", "NOTIFICATION_READ_FAILED", "CANCELLED" -> value
        else -> "PROCESSING_FAILED"
    }
}
