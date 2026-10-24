package app.shiv

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionTierPolicyTest {
    @Test fun missingTierUsesDefault() { assertEquals(ActionTier.FREE, ActionTierPolicy.resolve(null)) }
    @Test fun minimumCannotBeLowered() { assertEquals(ActionTier.ASK_FIRST, ActionTierPolicy.resolve("FREE", minimum = ActionTier.ASK_FIRST)) }
    @Test fun corruptTierFailsClosed() { assertEquals(ActionTier.ASK_FIRST, ActionTierPolicy.resolve("CORRUPT")) }
    @Test fun userCannotLowerAskFirstMinimumEvenWithActThenTell() {
        assertEquals(ActionTier.ASK_FIRST,
            ActionTierPolicy.resolve("ACT_THEN_TELL", minimum = ActionTier.ASK_FIRST))
    }
    @Test fun unrecognizedTierCannotAuthorizeFreeOperation() {
        assertEquals(ActionTier.ASK_FIRST,
            ActionTierPolicy.resolve("FREE ", minimum = ActionTier.FREE))
        assertEquals(ActionTier.ASK_FIRST,
            ActionTierPolicy.resolve("", minimum = ActionTier.FREE))
        assertEquals(ActionTier.ASK_FIRST,
            ActionTierPolicy.resolve("act_then_tell", minimum = ActionTier.FREE))
    }
    @Test fun validTierIsPreserved() { assertEquals(ActionTier.ACT_THEN_TELL, ActionTierPolicy.resolve("ACT_THEN_TELL")) }
}
