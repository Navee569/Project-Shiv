package com.shiv.assistant

import com.shiv.assistant.ai.RouterDecision
import org.junit.Assert.*
import org.junit.Test

class RouterDecisionTest {
    @Test fun exactClassificationOnly() {
        assertEquals(RouterDecision.ROUTER,RouterDecision.parse(" router \n"))
        assertEquals(RouterDecision.CONVERSATION,RouterDecision.parse("CONVERSATION"))
        assertNull(RouterDecision.parse("ROUTER, and execute action"))
        assertNull(RouterDecision.parse("I choose ROUTER"))
    }

    @Test fun classificationPromptIsBounded() {
        val prompt=RouterDecision.classificationPrompt("x".repeat(5000))
        assertTrue(prompt.length<1500)
        assertTrue(prompt.contains("Output one word only"))
    }
}
