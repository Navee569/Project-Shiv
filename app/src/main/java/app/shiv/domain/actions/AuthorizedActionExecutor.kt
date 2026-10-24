package app.shiv.domain.actions

import app.shiv.domain.model.ActionLogEntry
import java.util.UUID
import kotlinx.coroutines.CancellationException

fun interface ActionAuditRepository {
    suspend fun record(entry: ActionLogEntry)
}

fun interface ActionClock {
    fun nowMillis(): Long
}

data class ActionDispatchResult(
    val result: String,
    val undoInfo: String? = null
)

enum class ActionExecutionStatus {
    AWAITING_CONFIRMATION,
    DENIED_INVALID_REQUEST,
    DENIED_PERMISSION,
    STARTED,
    SUCCEEDED,
    FAILED,
    AUDIT_UNAVAILABLE
}

data class ActionExecutionReport(
    val authorization: ActionAuthorizationDecision,
    val status: ActionExecutionStatus,
    val result: String? = null,
    val dispatched: Boolean = false,
    val dispatchAttempted: Boolean = false
)

/** Executes only authorized actions and records their full request and completion state. */
class AuthorizedActionExecutor(
    private val auditRepository: ActionAuditRepository,
    private val clock: ActionClock = ActionClock(System::currentTimeMillis),
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {
    suspend fun execute(
        request: ActionAuthorizationRequest,
        dispatch: suspend () -> ActionDispatchResult
    ): ActionExecutionReport {
        val startedAt = clock.nowMillis()
        val decision = ActionAuthorizationPolicy.authorize(request, startedAt)
        val auditId = newId()
        val confirmationState = when {
            decision.outcome == ActionAuthorizationOutcome.REQUIRE_CONFIRMATION -> "PENDING"
            request.confirmed -> "CONFIRMED"
            else -> "NOT_REQUIRED"
        }

        if (decision.outcome != ActionAuthorizationOutcome.ALLOW) {
            val status = when (decision.outcome) {
                ActionAuthorizationOutcome.REQUIRE_CONFIRMATION -> ActionExecutionStatus.AWAITING_CONFIRMATION
                ActionAuthorizationOutcome.DENY_PERMISSION -> ActionExecutionStatus.DENIED_PERMISSION
                ActionAuthorizationOutcome.DENY_INVALID_REQUEST -> ActionExecutionStatus.DENIED_INVALID_REQUEST
                ActionAuthorizationOutcome.ALLOW -> error("Handled above")
            }
            val recorded = record(
                auditEntry(auditId, request, decision, startedAt, status, confirmationState)
            )
            return ActionExecutionReport(
                decision,
                if (recorded) status else ActionExecutionStatus.AUDIT_UNAVAILABLE
            )
        }

        val startedEntry = auditEntry(
            auditId, request, decision, startedAt, ActionExecutionStatus.STARTED, confirmationState
        )
        if (!record(startedEntry)) {
            return ActionExecutionReport(decision, ActionExecutionStatus.AUDIT_UNAVAILABLE)
        }

        val dispatchResult = try {
            dispatch()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val failed = startedEntry.copy(
                outcome = ActionExecutionStatus.FAILED.name,
                result = "FAILED:${failure.javaClass.simpleName}"
            )
            val recorded = record(failed)
            return ActionExecutionReport(
                decision,
                if (recorded) ActionExecutionStatus.FAILED else ActionExecutionStatus.AUDIT_UNAVAILABLE,
                failed.result,
                dispatchAttempted = true
            )
        }

        val completed = startedEntry.copy(
            outcome = ActionExecutionStatus.SUCCEEDED.name,
            result = dispatchResult.result,
            undoInfo = dispatchResult.undoInfo
        )
        val recorded = record(completed)
        return ActionExecutionReport(
            decision,
            if (recorded) ActionExecutionStatus.SUCCEEDED else ActionExecutionStatus.AUDIT_UNAVAILABLE,
            dispatchResult.result,
            dispatched = true,
            dispatchAttempted = true
        )
    }

    private suspend fun record(entry: ActionLogEntry): Boolean = try {
        auditRepository.record(entry)
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    private fun auditEntry(
        id: String,
        request: ActionAuthorizationRequest,
        decision: ActionAuthorizationDecision,
        createdAt: Long,
        status: ActionExecutionStatus,
        confirmationState: String
    ) = ActionLogEntry(
        id = id,
        action = "Action ${request.actionType}",
        tier = decision.effectiveTier.name,
        outcome = status.name,
        createdAt = createdAt,
        actionType = request.actionType,
        arguments = request.arguments.toMap(),
        permissionState = request.permissionState.name,
        confirmationState = confirmationState
    )
}

