package app.shiv.domain.repository

import app.shiv.domain.model.CaptureSignal
import app.shiv.domain.model.EventRecord
import app.shiv.domain.model.PendingEventRecord
import app.shiv.domain.model.RecordSourceMetadata
import app.shiv.domain.model.ReminderRecord

data class CaptureLease(val signal: CaptureSignal, val token: String)

data class CaptureOutcome(
    val event: EventRecord? = null,
    val pendingEvent: PendingEventRecord? = null,
    val reminder: ReminderRecord? = null,
    val sourceMetadata: RecordSourceMetadata? = null
)

/** Durable encrypted inbox; processing claims and resulting local records are committed atomically. */
interface CaptureQueueRepository {
    suspend fun enqueue(signal: CaptureSignal): Boolean
    suspend fun get(id: String): CaptureSignal?
    suspend fun all(): List<CaptureSignal>
    suspend fun claim(id: String, now: Long, leaseMillis: Long): CaptureLease?
    suspend fun claimNext(now: Long, leaseMillis: Long): CaptureLease?
    suspend fun complete(lease: CaptureLease, outcome: CaptureOutcome, completedAt: Long): Boolean
    suspend fun retry(lease: CaptureLease, nextAttemptAt: Long, failureCode: String): Boolean
    suspend fun markFailed(lease: CaptureLease, failureCode: String): Boolean
    suspend fun clear()
}
