package com.shiv.assistant.model

import com.shiv.assistant.domain.ComputeLease
import com.shiv.assistant.domain.PowerMode
import org.junit.Assert.*
import org.junit.Test

class LocalModelRoutingTest {
    private fun lease(allowed: Boolean = true, big: Boolean = false) =
        ComputeLease(allowed, PowerMode.BALANCED, "test", if (allowed) 15 else 0, big)

    @Test fun eligibleConversationUsesOwnerSelectedLargeModel() {
        val choice = LocalModelRouting.choose(lease(big = true), "small", "large",
            setOf("small", "large"))
        assertEquals("large", choice.modelId)
        assertTrue(choice.isConversationModel)
    }
    @Test fun lowResourceFallsBackToOwnerSelectedSmallModel() {
        val choice = LocalModelRouting.choose(lease(big = false), "small", "large",
            setOf("small", "large"))
        assertEquals("small", choice.modelId)
        assertFalse(choice.isConversationModel)
    }
    @Test fun noSmallModelNeverForcesLargeIntoLowRam() {
        val choice = LocalModelRouting.choose(lease(big = false), null, "large",
            setOf("large"))
        assertNull(choice.modelId)
    }
    @Test fun unregisteredModelsAndDeniedLeasesNeverRun() {
        assertNull(LocalModelRouting.choose(lease(big = true), "old", "absent",
            emptySet()).modelId)
        val blocked = LocalModelRouting.choose(lease(allowed = false, big = true),
            "small", "large", setOf("small", "large"))
        assertNull(blocked.modelId)
    }
    @Test fun sameModelIdNeverBypassesRouterFallback() {
        val selected = LocalModelRouting.choose(lease(big = true), "same", "same",
            setOf("same"))
        assertEquals("same", selected.modelId)
        assertFalse(selected.isConversationModel)
    }
}
