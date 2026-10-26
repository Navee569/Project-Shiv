package com.shiv.assistant.domain

import org.junit.Assert.*
import org.junit.Test

class PostCallPolicyTest {
    @Test fun onlyCompletedOwnerAllowedCallsYieldPrompt() {
        assertTrue(PostCallPolicy.shouldPrompt(CallPhase.OFFHOOK,CallPhase.IDLE,true,true,true,11))
        assertFalse(PostCallPolicy.shouldPrompt(CallPhase.RINGING,CallPhase.IDLE,true,true,true,11))
        assertFalse(PostCallPolicy.shouldPrompt(CallPhase.OFFHOOK,CallPhase.IDLE,false,true,true,11))
        assertFalse(PostCallPolicy.shouldPrompt(CallPhase.OFFHOOK,CallPhase.IDLE,true,false,true,11))
        assertFalse(PostCallPolicy.shouldPrompt(CallPhase.OFFHOOK,CallPhase.IDLE,true,true,false,11))
        assertFalse(PostCallPolicy.shouldPrompt(CallPhase.OFFHOOK,CallPhase.IDLE,true,true,true,23))
    }
}
