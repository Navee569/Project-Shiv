package app.shiv.domain.actions

import app.shiv.domain.model.ActionLogEntry
import app.shiv.domain.integration.DiagnosticArea
import app.shiv.domain.integration.DiagnosticCode
import app.shiv.domain.integration.DiagnosticEvent
import app.shiv.domain.integration.DiagnosticsPort
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
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val diagnostics: DiagnosticsPort = DiagnosticsPort { }
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

        // Audit persistence can suspend long enough for a time-sensitive proposal to expire.
        // Re-evaluate against the clock immediately before dispatch, not the earlier decision.
        val finalDecision = ActionAuthorizationPolicy.authorize(request, clock.nowMillis())
        if (finalDecision.outcome != ActionAuthorizationOutcome.ALLOW) {
            val finalStatus = when (finalDecision.outcome) {
                ActionAuthorizationOutcome.DENY_INVALID_REQUEST -> ActionExecutionStatus.DENIED_INVALID_REQUEST
                ActionAuthorizationOutcome.DENY_PERMISSION -> ActionExecutionStatus.DENIED_PERMISSION
                ActionAuthorizationOutcome.REQUIRE_CONFIRMATION -> ActionExecutionStatus.AWAITING_CONFIRMATION
                ActionAuthorizationOutcome.ALLOW -> error("Handled above")
            }
            val denied = startedEntry.copy(outcome = finalStatus.name, result = "Rejected at dispatch-time authorization")
            val recorded = record(denied)
            return ActionExecutionReport(
                finalDecision,
                if (recorded) finalStatus else ActionExecutionStatus.AUDIT_UNAVAILABLE
            )
        }

        val dispatchResult = try {
            dispatch()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            recordDiagnostic(DiagnosticCode.ACTION_DISPATCH_FAILED)
            val failed = startedEntry.copy(
                outcome = ActionExecutionStatus.FAILED.name,
                result = "FAILED:${failure.javaClass.simpleName}"
            )
            val recorded = record(failed)
            return ActionExecutionReport(
                finalDecision,
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
            finalDecision,
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
        recordDiagnostic(DiagnosticCode.AUDIT_WRITE_FAILED)
        false
    }

    private suspend fun recordDiagnostic(code: DiagnosticCode) {
        try {
            diagnostics.record(DiagnosticEvent(DiagnosticArea.ACTION, code, clock.nowMillis()))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Diagnostics failure must not change authorization or action outcome.
        }
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

