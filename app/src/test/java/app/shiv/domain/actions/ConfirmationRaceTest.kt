package app.shiv.domain.actions

import app.shiv.domain.model.ActionLogEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ConfirmationRaceTest {
    @Test
    fun doubleEventSaveCannotDispatchWhileFirstAuditIsSuspended() = runBlocking {
        val gate = PendingEventClaimGate()
        val first = gate.tryAcquire("event-1")!!
        val auditEntered = CompletableDeferred<Unit>()
        val resumeAudit = CompletableDeferred<Unit>()
        var dispatches = 0
        val executor = AuthorizedActionExecutor(ActionAuditRepository { entry ->
            if (entry.outcome == ActionExecutionStatus.STARTED.name) {
                auditEntered.complete(Unit)
                resumeAudit.await()
            }
        }, clock = ActionClock { 1_000L })
        val request = ActionAuthorizationRequest(
            "CREATE_EVENT", mapOf("id" to "event-1", "title" to "Appointment", "startsAt" to "2000"),
            ActionTier.FREE.name, ActionTier.FREE, confirmed = true
        )

        val saving = async {
            try {
                executor.execute(request) { dispatches++; ActionDispatchResult("saved") }
            } finally { first.close() }
        }
        auditEntered.await()
        assertNull(gate.tryAcquire("event-1"))
        resumeAudit.complete(Unit)
        assertEquals(ActionExecutionStatus.SUCCEEDED, saving.await().status)
        assertEquals(1, dispatches)
    }

    @Test
    fun cancelAfterConfirmClaimCannotRecordCancelOrStopCapturedDispatch() = runBlocking {
        val coordinator = PendingActionCoordinator<String>()
        coordinator.pendingAction.value = "save profile"
        val claim = coordinator.tryClaim()!!
        val auditEntered = CompletableDeferred<Unit>()
        val resumeAudit = CompletableDeferred<Unit>()
        val entries = mutableListOf<ActionLogEntry>()
        var dispatches = 0
        val executor = AuthorizedActionExecutor(ActionAuditRepository { entry ->
            if (entry.outcome == ActionExecutionStatus.STARTED.name) {
                auditEntered.complete(Unit)
                resumeAudit.await()
            }
            entries += entry
        })
        val request = ActionAuthorizationRequest(
            "SAVE_PROFILE", mapOf("name" to "Ada", "notes" to ""),
            ActionTier.ASK_FIRST.name, ActionTier.FREE, confirmed = true
        )

        val saving = async {
            try {
                executor.execute(request) { dispatches++; ActionDispatchResult("saved") }
            } finally { claim.close() }
        }
        auditEntered.await()
        assertNull(coordinator.cancel())
        assertNull(coordinator.tryClaim())
        assertNull(coordinator.pendingAction.value)
        resumeAudit.complete(Unit)
        assertEquals(ActionExecutionStatus.SUCCEEDED, saving.await().status)
        assertEquals(1, dispatches)
        assertFalse(entries.any { it.outcome == "Cancelled by user" })
    }

    @Test
    fun cancelBeforeClaimPreventsConfirmation() {
        val coordinator = PendingActionCoordinator<String>()
        coordinator.pendingAction.value = "save profile"
        assertEquals("save profile", coordinator.cancel())
        assertNull(coordinator.tryClaim())
    }
}
