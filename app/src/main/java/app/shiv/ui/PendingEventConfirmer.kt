package app.shiv.ui

import app.shiv.domain.actions.ActionAuthorizationRequest
import app.shiv.domain.actions.ActionDispatchResult
import app.shiv.domain.actions.ActionExecutionReport
import app.shiv.domain.actions.ActionExecutionStatus
import app.shiv.domain.actions.ActionTier
import app.shiv.domain.actions.AuthorizedActionExecutor
import app.shiv.domain.integration.ReminderSchedulingPort
import app.shiv.domain.model.ActionLogEntry
import app.shiv.domain.model.ChatMessage
import app.shiv.domain.model.EventRecord
import app.shiv.domain.model.PendingEventRecord
import app.shiv.domain.repository.ShivViewRepository
import kotlinx.coroutines.flow.first

/** Confirms a listening candidate while keeping definitively undelivered actions retryable. */
internal class PendingEventConfirmer(
    private val repository: ShivViewRepository,
    private val executor: AuthorizedActionExecutor,
    private val reminders: ReminderSchedulingPort,
    private val failureText: (ActionExecutionReport) -> String,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    suspend fun confirm(
        candidate: PendingEventRecord,
        request: ActionAuthorizationRequest,
        leadMinutes: Int
    ): ActionExecutionReport? {
        if (repository.pendingEvents(0).first().none { it.id == candidate.id && it == candidate }) return null
        if (candidate.startsAt <= nowMillis()) {
            repository.deletePendingEvent(candidate.id)
            repository.logAction(ActionLogEntry(
                action = "Expired a listening event confirmation",
                tier = ActionTier.ASK_FIRST.name,
                outcome = "Event time passed"
            ))
            return null
        }

        val report = executor.execute(request) {
            val event = EventRecord(
                id = candidate.id,
                title = candidate.title,
                startsAt = candidate.startsAt,
                place = candidate.place,
                person = candidate.person,
                source = "LISTENING",
                createdAt = candidate.createdAt
            )
            repository.saveEvent(event)
            repository.deletePendingEvent(candidate.id)
            reminders.schedule(event, leadMinutes)
            ActionDispatchResult("Listening event saved", "DELETE_EVENT:${event.id}")
        }
        if (report.status != ActionExecutionStatus.SUCCEEDED) {
            // A failed STARTED audit has no side effect. Once dispatch was attempted, its
            // result may be partial or uncertain, so the candidate must not be retried.
            if (report.dispatchAttempted || report.status != ActionExecutionStatus.AUDIT_UNAVAILABLE) {
                repository.deletePendingEvent(candidate.id)
            }
            repository.addMessage(ChatMessage(text = failureText(report), fromUser = false))
        }
        return report
    }
}
