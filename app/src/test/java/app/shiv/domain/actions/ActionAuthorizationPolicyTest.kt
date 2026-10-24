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
}
