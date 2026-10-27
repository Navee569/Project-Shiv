package com.shiv.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class MicrophoneSessionPolicyTest {
    @Test fun wakePhraseIsOnlyACommandGateAndPocketNeedsBothSignals() {
        assertEquals("meet Raj tomorrow",MicrophoneSessionPolicy.wakeCommand(
            "Shiv, meet Raj tomorrow",true))
        assertNull(MicrophoneSessionPolicy.wakeCommand("meet Raj tomorrow",true))
        assertNull(MicrophoneSessionPolicy.wakeCommand("Shiv, my OTP is 123456",true))
        assertTrue(MicrophoneSessionPolicy.wakeOnly("Shiv!"))
        assertFalse(MicrophoneSessionPolicy.wakeOnly("Shiv, meet Raj"))
        assertTrue(MicrophoneSessionPolicy.pocketed(0f,5f,2f))
        assertFalse(MicrophoneSessionPolicy.pocketed(0f,5f,40f))
        assertFalse(MicrophoneSessionPolicy.pocketed(null,5f,2f))
        assertTrue(MicrophoneSessionPolicy.sleepHours(23))
        assertTrue(MicrophoneSessionPolicy.sleepHours(6))
        assertFalse(MicrophoneSessionPolicy.sleepHours(12))
    }
    @Test fun sessionExpiresOrStopsForLowPowerHeatErrorsAndMissingLocalSpeech() {
        val start=1_000_000L
        assertTrue(MicrophoneSessionPolicy.mayContinue(start,start+1000,70,0,true,0))
        assertFalse(MicrophoneSessionPolicy.mayContinue(start,start+600001,70,0,true,0))
        assertFalse(MicrophoneSessionPolicy.mayContinue(start,start+1000,24,0,true,0))
        assertFalse(MicrophoneSessionPolicy.mayContinue(start,start+1000,80,4,true,0))
        assertFalse(MicrophoneSessionPolicy.mayContinue(start,start+1000,80,0,false,0))
        assertFalse(MicrophoneSessionPolicy.mayContinue(start,start+1000,80,0,true,5))
        assertTrue(MicrophoneSessionPolicy.mayContinue(start,start+600001,70,0,true,0,true))
        assertFalse(MicrophoneSessionPolicy.mayContinue(start,start+600001,24,0,true,0,true))
        assertEquals(1_500L,MicrophoneSessionPolicy.retryDelayMs(0))
        assertEquals(15_000L,MicrophoneSessionPolicy.retryDelayMs(6))
    }
    @Test fun microphoneDoesNotPersistSensitiveRecognizedCodes() {
        assertNull(MicrophoneSessionPolicy.safeUtterance("OTP is 123456"))
        assertNull(MicrophoneSessionPolicy.safeUtterance("My password is hello"))
        assertNull(MicrophoneSessionPolicy.safeUtterance("123456789012"))
        assertEquals("Meeting tomorrow at noon",
            MicrophoneSessionPolicy.safeUtterance("  Meeting tomorrow at noon  "))
    }
}
