package app.shiv

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionTierPolicyTest {
    @Test fun missingTierUsesDefault() { assertEquals(ActionTier.FREE, ActionTierPolicy.resolve(null)) }
    @Test fun minimumCannotBeLowered() { assertEquals(ActionTier.ASK_FIRST, ActionTierPolicy.resolve("FREE", minimum = ActionTier.ASK_FIRST)) }
    @Test fun corruptTierFailsClosed() { assertEquals(ActionTier.ASK_FIRST, ActionTierPolicy.resolve("CORRUPT")) }
    @Test fun validTierIsPreserved() { assertEquals(ActionTier.ACT_THEN_TELL, ActionTierPolicy.resolve("ACT_THEN_TELL")) }
}
