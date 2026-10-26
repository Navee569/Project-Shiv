package com.shiv.assistant.domain

import org.junit.Assert.*
import org.junit.Test

class SignalFactsTest {
    @Test fun dateEventDetectionNeedsBothClues() {
        val event = SignalFacts.extract("Meeting tomorrow at 5 pm")
        assertNotNull(event)
        assertTrue(event!!.confidence < 1.0)
        assertNull(SignalFacts.extract("hello friend"))
        assertNull(SignalFacts.extract("tomorrow is the day"))
    }
    @Test fun supportsLanguageMarkersWithoutInferringCertainty() {
        assertNotNull(SignalFacts.extract("ನಾಳೆ ಸಭೆ"))
        assertNotNull(SignalFacts.extract("రేపు సమావేశం"))
    }
    @Test fun noClaimWithoutEvidence() {
        assertNull(SignalFacts.extract(""))
        assertNull(SignalFacts.extract("Quick shopping"))
    }
}
