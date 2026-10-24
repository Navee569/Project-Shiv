package app.shiv.domain.integration

import app.shiv.domain.model.CaptureSignal
import app.shiv.domain.repository.CaptureQueueRepository

/** Commits an incoming signal before scheduling any Brain-facing work. */
class CaptureSignalIntake(
    private val repository: CaptureQueueRepository,
    private val scheduleProcessing: suspend (CaptureSignal) -> Unit
) {
    suspend fun receive(signal: CaptureSignal): Boolean {
        if (!repository.enqueue(signal)) return false
        scheduleProcessing(signal)
        return true
    }
}
