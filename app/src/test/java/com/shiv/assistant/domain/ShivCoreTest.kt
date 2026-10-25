package com.shiv.assistant.domain

import org.junit.Assert.*
import org.junit.Test

class ShivCoreTest {
    @Test fun capabilitiesHaveUniqueIds() {
        assertTrue(ShivCapabilities.all.size >= 40)
        assertEquals(ShivCapabilities.all.size, ShivCapabilities.all.map { it.id }.distinct().size)
    }
    @Test fun localModeNeverTransmits() {
        assertEquals(IntelligenceMode.LOCAL, AssistantConfiguration().mode)
        assertFalse(CloudSafety.maySend(AssistantConfiguration()))
    }
    @Test fun cloudRequiresConsentAndHttps() {
        assertFalse(CloudSafety.maySend(AssistantConfiguration(IntelligenceMode.CLOUD, "https://api.example.org/v1/chat/completions", "test", false)))
        assertTrue(CloudSafety.maySend(AssistantConfiguration(IntelligenceMode.CLOUD, "https://api.example.org/v1/chat/completions", "test", true)))
        assertFalse(CloudSafety.endpointAllowed("http://api.example.org/chat"))
        assertFalse(CloudSafety.endpointAllowed("https://user:pass@example.org/chat"))
        assertTrue(CloudSafety.endpointAllowed("http://localhost:8080/v1/chat/completions"))
    }
    @Test fun sendingMoneyOrMessagesAlwaysNeedsApproval() {
        listOf("call 123","sms hello","telegram hello","whatsapp hello","pay 500","post hello").forEach {
            assertEquals(Risk.USER_CONFIRMATION, ShivPlanner.parse(it).risk)
        }
    }
    @Test fun unknownActionsFailClosed() {
        val p = ShivPlanner.parse("transfer money and delete data")
        assertEquals(ActionKind.UNKNOWN, p.kind)
        assertEquals(Risk.READ_ONLY, p.risk)
    }
    @Test fun compoundPlansAreBounded() {
        val steps = ShivPlanner.plan("call 123 then sms done then navigate airport").steps
        assertEquals(listOf(ActionKind.DIAL,ActionKind.SMS,ActionKind.MAPS), steps.map { it.kind })
        assertEquals(12, ShivPlanner.plan(List(30) { "call 123" }.joinToString(" then ")).steps.size)
    }
    @Test fun memoryDoesNotRequestExternalAction() {
        val p = ShivPlanner.parse("remember drink water")
        assertEquals(ActionKind.NOTES, p.kind)
        assertEquals(Risk.READ_ONLY, p.risk)
    }
    @Test fun incompleteIntegrationsAreLabelled() {
        listOf("wakeword","model-local","accessibility","social-publish","terminal").forEach {
            assertNotEquals(CapabilityState.BUILT_IN,ShivCapabilities.all.first { c -> c.id == it }.state)
        }
    }
}
