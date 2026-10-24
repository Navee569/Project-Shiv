package app.shiv.domain.integration

import app.shiv.domain.repository.CaptureOutcome
import app.shiv.domain.repository.CaptureQueueRepository
import kotlinx.coroutines.CancellationException

enum class CaptureRunResult { COMPLETED, ALREADY_COMPLETED, RETRY_SCHEDULED, FAILED, BUSY, NOT_FOUND }
data class CaptureRun(val result: CaptureRunResult, val signalId: String? = null)

/** Claims an inbox row, runs Brain work after persistence, and commits its result with completion. */
class CaptureQueueProcessor(
    private val repository: CaptureQueueRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val leaseMillis: Long = 5 * 60_000L,
    private val maxAttempts: Int = 8,
    private val processSignal: suspend (app.shiv.domain.model.CaptureSignal) -> CaptureOutcome
) {
    suspend fun process(id: String): CaptureRunResult {
        val now = nowMillis()
        val lease = repository.claim(id, now, leaseMillis)
        if (lease == null) return when (repository.get(id)?.status) {
            null -> CaptureRunResult.NOT_FOUND
            app.shiv.domain.model.CaptureStatus.COMPLETED -> CaptureRunResult.ALREADY_COMPLETED
            app.shiv.domain.model.CaptureStatus.FAILED -> CaptureRunResult.FAILED
            else -> CaptureRunResult.BUSY
        }
        return process(lease).result
    }

    suspend fun processNext(): CaptureRun {
        val lease = repository.claimNext(nowMillis(), leaseMillis) ?: return CaptureRun(CaptureRunResult.NOT_FOUND)
        return process(lease)
    }

    private suspend fun process(lease: app.shiv.domain.repository.CaptureLease): CaptureRun {
        return try {
            val outcome = processSignal(lease.signal)
            CaptureRun(
                if (repository.complete(lease, outcome, nowMillis())) CaptureRunResult.COMPLETED else CaptureRunResult.BUSY,
                lease.signal.id
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (lease.signal.attempts >= maxAttempts) {
                repository.markFailed(lease, "PROCESSING_FAILED")
                CaptureRun(CaptureRunResult.FAILED, lease.signal.id)
            } else {
                val retryDelay = (1_000L shl ((lease.signal.attempts - 1).coerceIn(0, 16))).coerceAtMost(6 * 60 * 60 * 1000L)
                repository.retry(lease, nowMillis() + retryDelay, "PROCESSING_FAILED")
                CaptureRun(CaptureRunResult.RETRY_SCHEDULED, lease.signal.id)
            }
        }
    }
}
