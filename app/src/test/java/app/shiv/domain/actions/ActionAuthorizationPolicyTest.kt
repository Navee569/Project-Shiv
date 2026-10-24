package app.shiv.domain.actions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionAuthorizationPolicyTest {
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

        val pending = ActionAuthorizationPolicy.authorize(request)
        assertEquals(ActionAuthorizationOutcome.REQUIRE_CONFIRMATION, pending.outcome)
        assertEquals(ActionTier.ASK_FIRST, pending.effectiveTier)
        assertEquals(request.arguments, pending.request.arguments)
        assertEquals(ActionAuthorizationOutcome.ALLOW, ActionAuthorizationPolicy.authorize(request.copy(confirmed = true)).outcome)
    }

    @Test
    fun authorizationDeniesMissingPermissionEvenWhenConfirmed() {
        val decision = ActionAuthorizationPolicy.authorize(
            ActionAuthorizationRequest(
                actionType = "READ_CALENDAR",
                arguments = emptyMap(),
                configuredTier = "FREE",
                minimumTier = ActionTier.FREE,
                permissionState = ActionPermissionState.DENIED,
                confirmed = true
            )
        )

        assertEquals(ActionAuthorizationOutcome.DENY_PERMISSION, decision.outcome)
    }

    @Test
    fun authorizationRejectsMalformedActionIdentity() {
        val decision = ActionAuthorizationPolicy.authorize(
            ActionAuthorizationRequest(" ", emptyMap(), null, ActionTier.FREE)
        )

        assertEquals(ActionAuthorizationOutcome.DENY_INVALID_REQUEST, decision.outcome)
    }
}
