package app.shiv.domain.actions

import app.shiv.domain.model.ActionLogEntry
import app.shiv.domain.integration.DiagnosticCode
import app.shiv.domain.integration.DiagnosticEvent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorizedActionExecutorTest {
    private val now = 1_000L

    @Test
    fun confirmationIsAuditedAndOnlyConfirmedRequestDispatches() = runBlocking {
        val audit = InMemoryActionAuditRepository()
        val executor = executor(audit)
        var dispatchCount = 0
        val pendingRequest = createEventRequest(configuredTier = ActionTier.ASK_FIRST.name)

        val pending = executor.execute(pendingRequest) {
            dispatchCount++
            ActionDispatchResult("saved")
        }

        assertEquals(ActionExecutionStatus.AWAITING_CONFIRMATION, pending.status)
        assertFalse(pending.dispatched)
        assertEquals(0, dispatchCount)
        assertEquals("PENDING", audit.entries.single().confirmationState)

        val confirmed = executor.execute(pendingRequest.copy(confirmed = true)) {
            assertEquals(ActionExecutionStatus.STARTED.name, audit.entries.last().outcome)
            dispatchCount++
            ActionDispatchResult("event saved", "DELETE_EVENT:event-1")
        }

        assertEquals(ActionExecutionStatus.SUCCEEDED, confirmed.status)
        assertTrue(confirmed.dispatched)
        assertEquals(1, dispatchCount)
        val completed = audit.entries.last()
        assertEquals("CREATE_EVENT", completed.actionType)
        assertEquals(mapOf("id" to "event-1", "title" to "Meeting", "startsAt" to "2000"), completed.arguments)
        assertEquals(ActionPermissionState.NOT_REQUIRED.name, completed.permissionState)
        assertEquals("CONFIRMED", completed.confirmationState)
        assertEquals("event saved", completed.result)
        assertEquals("DELETE_EVENT:event-1", completed.undoInfo)
    }

    @Test
    fun confirmationIsDeniedWhenEventExpiresBeforeFinalAuthorization() = runBlocking {
        val audit = InMemoryActionAuditRepository()
        var now = 1_000L
        val executor = AuthorizedActionExecutor(
            auditRepository = audit,
            clock = ActionClock { now },
            newId = { "audit-${audit.entries.size + 1}" }
        )
        val request = createEventRequest(configuredTier = ActionTier.ASK_FIRST.name)
        val pending = executor.execute(request) { ActionDispatchResult("unexpected") }
        assertEquals(ActionExecutionStatus.AWAITING_CONFIRMATION, pending.status)

        now = 2_000L
        var dispatched = false
        val confirmed = executor.execute(request.copy(confirmed = true)) {
            dispatched = true
            ActionDispatchResult("unexpected")
        }

        assertEquals(ActionExecutionStatus.DENIED_INVALID_REQUEST, confirmed.status)
        assertFalse(confirmed.dispatched)
        assertFalse(dispatched)
    }

    @Test
    fun eventThatExpiresWhileStartedAuditWriteIsSuspendedIsRejected() = runBlocking {
        var now = 1_000L
        val writeEntered = CompletableDeferred<Unit>()
        val allowWrite = CompletableDeferred<Unit>()
        val entries = mutableListOf<ActionLogEntry>()
        val audit = ActionAuditRepository { entry ->
            if (entry.outcome == ActionExecutionStatus.STARTED.name) {
                writeEntered.complete(Unit)
                allowWrite.await()
            }
            entries += entry
        }
        val executor = AuthorizedActionExecutor(auditRepository = audit, clock = ActionClock { now })
        var dispatched = false

        val pending = async {
            executor.execute(createEventRequest(configuredTier = ActionTier.ASK_FIRST.name).copy(confirmed = true)) {
                dispatched = true
                ActionDispatchResult("unexpected")
            }
        }
        writeEntered.await()
        now = 2_000L
        allowWrite.complete(Unit)
        val report = pending.await()

        assertEquals(ActionExecutionStatus.DENIED_INVALID_REQUEST, report.status)
        assertFalse(report.dispatched)
        assertFalse(dispatched)
        assertEquals(ActionExecutionStatus.DENIED_INVALID_REQUEST.name, entries.last().outcome)
    }

    @Test
    fun invalidConfirmedActionIsAuditedAndNeverDispatched() = runBlocking {
        val audit = InMemoryActionAuditRepository()
        val executor = executor(audit)
        var dispatched = false

        val report = executor.execute(
            ActionAuthorizationRequest(
                actionType = "UNKNOWN_ACTION",
                arguments = emptyMap(),
                configuredTier = ActionTier.FREE.name,
                minimumTier = ActionTier.FREE,
                confirmed = true
            )
        ) {
            dispatched = true
            ActionDispatchResult("unexpected")
        }

        assertEquals(ActionExecutionStatus.DENIED_INVALID_REQUEST, report.status)
        assertFalse(report.dispatched)
        assertFalse(dispatched)
        assertEquals(ActionExecutionStatus.DENIED_INVALID_REQUEST.name, audit.entries.single().outcome)
    }

    @Test
    fun failureToRecordStartPreventsDispatch() = runBlocking {
        val audit = InMemoryActionAuditRepository(failOnWrite = 1)
        val executor = executor(audit)
        var dispatched = false

        val report = executor.execute(createEventRequest()) {
            dispatched = true
            ActionDispatchResult("unexpected")
        }

        assertEquals(ActionExecutionStatus.AUDIT_UNAVAILABLE, report.status)
        assertFalse(report.dispatched)
        assertFalse(dispatched)
        assertTrue(audit.entries.isEmpty())
    }

    @Test
    fun completionAuditFailureReportsThatDispatchAlreadySucceeded() = runBlocking {
        val audit = InMemoryActionAuditRepository(failOnWrite = 2)
        val report = executor(audit).execute(createEventRequest()) {
            ActionDispatchResult("event saved")
        }

        assertEquals(ActionExecutionStatus.AUDIT_UNAVAILABLE, report.status)
        assertTrue(report.dispatched)
        assertTrue(report.dispatchAttempted)
        assertEquals(ActionExecutionStatus.STARTED.name, audit.entries.single().outcome)
    }

    @Test
    fun dispatchFailureIsPersistedWithoutExceptionMessage() = runBlocking {
        val audit = InMemoryActionAuditRepository()
        val report = executor(audit).execute(createEventRequest()) {
            error("sensitive details")
        }

        assertEquals(ActionExecutionStatus.FAILED, report.status)
        assertFalse(report.dispatched)
        assertTrue(report.dispatchAttempted)
        assertEquals("FAILED:IllegalStateException", audit.entries.last().result)
        assertFalse(audit.entries.last().result.orEmpty().contains("sensitive details"))
    }

    @Test
    fun auditFailureEmitsOnlySanitizedDiagnosticCode() = runBlocking {
        val audit = InMemoryActionAuditRepository(failOnWrite = 1)
        val diagnostics = mutableListOf<DiagnosticEvent>()
        val executor = AuthorizedActionExecutor(
            auditRepository = audit,
            clock = ActionClock { now },
            diagnostics = { diagnostics += it }
        )

        val report = executor.execute(createEventRequest()) { ActionDispatchResult("unexpected") }

        assertEquals(ActionExecutionStatus.AUDIT_UNAVAILABLE, report.status)
        assertEquals(1, diagnostics.size)
        assertEquals(DiagnosticCode.AUDIT_WRITE_FAILED, diagnostics.single().code)
        assertEquals(now, diagnostics.single().occurredAtMillis)
    }

    @Test
    fun dispatchFailureEmitsSanitizedDiagnosticAndDoesNotExposeFailureText() = runBlocking {
        val audit = InMemoryActionAuditRepository()
        val diagnostics = mutableListOf<DiagnosticEvent>()
        val executor = AuthorizedActionExecutor(
            auditRepository = audit,
            clock = ActionClock { now },
            diagnostics = { diagnostics += it }
        )

        executor.execute(createEventRequest()) { error("private event title") }

        assertEquals(listOf(DiagnosticCode.ACTION_DISPATCH_FAILED), diagnostics.map { it.code })
        assertFalse(diagnostics.toString().contains("private event title"))
    }

    private fun executor(audit: InMemoryActionAuditRepository) = AuthorizedActionExecutor(
        auditRepository = audit,
        clock = ActionClock { now },
        newId = { "audit-${audit.entries.size + 1}" }
    )

    private fun createEventRequest(configuredTier: String? = ActionTier.FREE.name) = ActionAuthorizationRequest(
        actionType = "CREATE_EVENT",
        arguments = mapOf("id" to "event-1", "title" to "Meeting", "startsAt" to "2000"),
        configuredTier = configuredTier,
        minimumTier = ActionTier.FREE
    )

    private class InMemoryActionAuditRepository(private val failOnWrite: Int? = null) : ActionAuditRepository {
        val entries = mutableListOf<ActionLogEntry>()
        private var writes = 0

        override suspend fun record(entry: ActionLogEntry) {
            writes++
            if (writes == failOnWrite) error("audit unavailable")
            entries += entry
        }
    }
}
