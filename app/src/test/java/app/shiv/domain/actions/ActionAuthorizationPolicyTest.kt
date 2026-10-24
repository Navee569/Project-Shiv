package app.shiv.domain.actions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionAuthorizationPolicyTest {
    private val now = 1_700_000_000_000L
    private val future = now + 60_000L

    @Test
    fun invalidOrMissingConfigurationUsesMinimumTier() {
        assertEquals(ActionTier.FREE, ActionAuthorizationPolicy.effectiveTier(null, ActionTier.FREE))
        assertEquals(ActionTier.ACT_THEN_TELL, ActionAuthorizationPolicy.effectiveTier("unknown", ActionTier.ACT_THEN_TELL))
    }

    @Test
    fun configuredTierCannotLowerMinimum() {
        assertEquals(ActionTier.ACT_THEN_TELL, ActionAuthorizationPolicy.effectiveTier("FREE", ActionTier.ACT_THEN_TELL))
        assertEquals(ActionTier.ASK_FIRST, ActionAuthorizationPolicy.effectiveTier("FREE", ActionTier.ASK_FIRST))
    }

    @Test
    fun configuredAskFirstRaisesTier() {
        assertEquals(ActionTier.ASK_FIRST, ActionAuthorizationPolicy.effectiveTier("ASK_FIRST", ActionTier.FREE))
    }

    @Test
    fun backgroundAuthorizationRejectsAskFirstAndKeepsMinimum() {
        assertNull(ActionAuthorizationPolicy.backgroundTier("ASK_FIRST", ActionTier.ACT_THEN_TELL))
        assertEquals(ActionTier.ACT_THEN_TELL, ActionAuthorizationPolicy.backgroundTier("FREE", ActionTier.ACT_THEN_TELL))
        assertEquals(ActionTier.ACT_THEN_TELL, ActionAuthorizationPolicy.backgroundTier(null, ActionTier.ACT_THEN_TELL))
    }

    @Test
    fun authorizationRequiresConfirmationForAskFirstAndAllowsAfterConfirmation() {
        val request = ActionAuthorizationRequest(
            actionType = "SEND_MESSAGE",
            arguments = mapOf("person" to "Raj", "body" to "See you soon"),
            configuredTier = "FREE",
            minimumTier = ActionTier.ASK_FIRST
        )

        val pending = ActionAuthorizationPolicy.authorize(request, now)
        assertEquals(ActionAuthorizationOutcome.REQUIRE_CONFIRMATION, pending.outcome)
        assertEquals(ActionTier.ASK_FIRST, pending.effectiveTier)
        assertEquals(request.arguments, pending.request.arguments)
        assertEquals(ActionAuthorizationOutcome.ALLOW, ActionAuthorizationPolicy.authorize(request.copy(confirmed = true), now).outcome)
    }

    @Test
    fun authorizationDeniesMissingPermissionEvenWhenConfirmed() {
        val decision = authorize("READ_CALENDAR", emptyMap(), confirmed = true, permission = ActionPermissionState.DENIED)
        assertEquals(ActionAuthorizationOutcome.DENY_PERMISSION, decision.outcome)
    }

    @Test
    fun authorizationRejectsUnknownAndBlankActionIdentities() {
        assertEquals(ActionAuthorizationOutcome.DENY_INVALID_REQUEST, authorize("UNKNOWN_ACTION", emptyMap()).outcome)
        assertEquals(ActionAuthorizationOutcome.DENY_INVALID_REQUEST, authorize(" ", emptyMap()).outcome)
    }

    @Test
    fun authorizationRejectsMissingExtraAndMalformedArguments() {
        assertInvalid("CREATE_EVENT", mapOf("id" to "event-1", "title" to "Review"))
        assertInvalid("CREATE_EVENT", mapOf("id" to "event-1", "title" to "Review", "startsAt" to future.toString(), "extra" to "x"))
        assertInvalid("CREATE_EVENT", mapOf("id" to "event-1", "title" to " ", "startsAt" to future.toString()))
        assertInvalid("CREATE_EVENT", mapOf("id" to "event-1", "title" to "Review", "startsAt" to "tomorrow"))
        assertInvalid("SET_REMINDER", mapOf("minutes" to "later"))
        assertInvalid("SET_REMINDER", mapOf("minutes" to "-1"))
        assertInvalid("SET_REMINDER", mapOf("minutes" to "1441"))
        assertInvalid("SEND_MESSAGE", mapOf("person" to "Raj", "body" to "Hi", "unexpected" to "x"))
    }

    @Test
    fun eventTargetsMustBeStrictlyInTheFutureAtAuthorizationTime() {
        val eventRequest = mapOf("id" to "event-1", "title" to "Review")
        assertInvalid("CREATE_EVENT", eventRequest + ("startsAt" to (now - 1).toString()))
        assertInvalid("CREATE_EVENT", eventRequest + ("startsAt" to now.toString()))
        assertAllowed("CREATE_EVENT", eventRequest + ("startsAt" to future.toString()))

        val reschedule = mapOf("eventId" to "event-1", "startsAt" to future.toString())
        assertInvalid("RESCHEDULE", reschedule + ("newStartsAt" to now.toString()))
        assertAllowed("RESCHEDULE", reschedule + ("newStartsAt" to (future + 1).toString()))
    }

    @Test
    fun eventThatExpiresWhileAwaitingConfirmationIsRejectedWithoutWritesOrDispatch() {
        val request = ActionAuthorizationRequest(
            actionType = "CREATE_EVENT",
            arguments = mapOf("id" to "event-1", "title" to "Review", "startsAt" to future.toString()),
            configuredTier = "ASK_FIRST",
            minimumTier = ActionTier.FREE
        )
        assertEquals(ActionAuthorizationOutcome.REQUIRE_CONFIRMATION, ActionAuthorizationPolicy.authorize(request, now).outcome)

        val finalDecision = ActionAuthorizationPolicy.authorize(request.copy(confirmed = true), future)
        var writes = 0
        var dispatches = 0
        assertEquals(ActionAuthorizationOutcome.DENY_INVALID_REQUEST, finalDecision.outcome)
        assertFalse(finalDecision.dispatchIfAllowed {
            writes++
            dispatches++
        })
        assertEquals(0, writes)
        assertEquals(0, dispatches)
    }

    @Test
    fun reminderMinuteBoundariesRemainSupported() {
        assertAllowed("SET_REMINDER", mapOf("minutes" to "0"))
        assertAllowed("SET_REMINDER", mapOf("minutes" to "1440"))
        assertInvalid("SET_REMINDER", mapOf("minutes" to "-1"))
        assertInvalid("SET_REMINDER", mapOf("minutes" to "1441"))
    }

    @Test
    fun rejectedEventAuthorizationDoesNotInvokeWriteOrDispatchCallbacks() {
        var writes = 0
        var dispatches = 0
        val denied = authorize(
            "CREATE_EVENT",
            mapOf("id" to "event-1", "title" to "Review", "startsAt" to now.toString())
        )

        val dispatched = denied.dispatchIfAllowed {
            writes++
            dispatches++
        }

        assertFalse(dispatched)
        assertEquals(0, writes)
        assertEquals(0, dispatches)
    }

    @Test
    fun confirmationCannotMakeAnInvalidRequestAuthorizable() {
        assertEquals(
            ActionAuthorizationOutcome.DENY_INVALID_REQUEST,
            authorize("SET_REMINDER", mapOf("minutes" to "not-a-number"), confirmed = true).outcome
        )
        assertEquals(
            ActionAuthorizationOutcome.DENY_INVALID_REQUEST,
            authorize("CREATE_EVENT", mapOf("id" to "event-1", "title" to "Review", "startsAt" to "1"), confirmed = true).outcome
        )
    }

    @Test
    fun validSupportedActionsAreAccepted() {
        assertAllowed("CREATE_EVENT", mapOf("id" to "event-1", "title" to "Review", "startsAt" to future.toString()))
        assertAllowed("SET_REMINDER", mapOf("minutes" to "15"))
        assertAllowed("SAVE_MEMORY", mapOf("id" to "memory-1", "text" to "Prefers tea"))
        assertAllowed("SAVE_RULE", mapOf("key" to "reminder", "value" to "15"))
        assertAllowed("ASK_USER", mapOf("question" to "Which day?"))
        assertAllowed("SEND_NOTIFICATION", mapOf("title" to "Event", "body" to "Soon"))
        assertAllowed("STAY_QUIET", emptyMap())
        assertAllowed("SAVE_PROFILE", mapOf("name" to "", "notes" to ""))
        assertAllowed("RESCHEDULE", mapOf("eventId" to "event-1", "startsAt" to future.toString(), "newStartsAt" to (future + 1).toString()))
        assertAllowed("DRAFT_REPLY", mapOf("person" to "Raj", "body" to "Thanks"))
        assertAllowed("READ_CALENDAR", emptyMap())
        assertAllowed("DELETE_DATA", emptyMap())
        assertAllowed("DELETE_DATA", mapOf("eventId" to "event-1"))
        assertAllowed("DELETE_DATA", mapOf("memoryId" to "memory-1"))
    }

    private fun authorize(
        action: String,
        arguments: Map<String, String>,
        confirmed: Boolean = false,
        permission: ActionPermissionState = ActionPermissionState.NOT_REQUIRED
    ) = ActionAuthorizationPolicy.authorize(
        ActionAuthorizationRequest(action, arguments, "FREE", ActionTier.FREE, permission, confirmed),
        now
    )

    private fun assertInvalid(action: String, arguments: Map<String, String>) =
        assertEquals(ActionAuthorizationOutcome.DENY_INVALID_REQUEST, authorize(action, arguments).outcome)

    private fun assertAllowed(action: String, arguments: Map<String, String>) =
        assertEquals(ActionAuthorizationOutcome.ALLOW, authorize(action, arguments).outcome)
}
