package com.shiv.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class MicrophoneSessionPolicyTest {
    @Test fun sessionExpiresOrStopsForLowPowerHeatErrorsAndMissingLocalSpeech() {
        val start=1_000_000L
        assertTrue(MicrophoneSessionPolicy.mayContinue(start,start+1000,70,0,true,0))
        assertFalse(MicrophoneSessionPolicy.mayContinue(start,start+600001,70,0,true,0))
        assertFalse(MicrophoneSessionPolicy.mayContinue(start,start+1000,24,0,true,0))
        assertFalse(MicrophoneSessionPolicy.mayContinue(start,start+1000,80,4,true,0))
        assertFalse(MicrophoneSessionPolicy.mayContinue(start,start+1000,80,0,false,0))
        assertFalse(MicrophoneSessionPolicy.mayContinue(start,start+1000,80,0,true,5))
    }
    @Test fun microphoneDoesNotPersistSensitiveRecognizedCodes() {
        assertNull(MicrophoneSessionPolicy.safeUtterance("OTP is 123456"))
        assertNull(MicrophoneSessionPolicy.safeUtterance("My password is hello"))
        assertNull(MicrophoneSessionPolicy.safeUtterance("123456789012"))
        assertEquals("Meeting tomorrow at noon",
            MicrophoneSessionPolicy.safeUtterance("  Meeting tomorrow at noon  "))
    }
}
