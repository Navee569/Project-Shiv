package com.shiv.assistant.domain

import org.junit.Assert.*
import org.junit.Test

class ProactiveTriggerPolicyTest {
    @Test fun deadlineOnlyInsideUpcomingHour() {
        val now=1_000_000L
        assertFalse(ProactiveTriggerPolicy.approachingDeadline(now-1,now))
        assertTrue(ProactiveTriggerPolicy.approachingDeadline(now+60*60_000L,now))
        assertFalse(ProactiveTriggerPolicy.approachingDeadline(now+60*60_000L+1,now))
    }
    @Test fun longIdleRequiresFreshUnlockAfterTwoHours() {
        val shown=1_000_000L
        val hidden=shown+2*60*60_000L
        assertTrue(ProactiveTriggerPolicy.longIdleUnlock(shown,hidden,shown))
        assertFalse(ProactiveTriggerPolicy.longIdleUnlock(shown,hidden,hidden))
        assertFalse(ProactiveTriggerPolicy.longIdleUnlock(shown,hidden-1,shown))
        assertFalse(ProactiveTriggerPolicy.longIdleUnlock(null,hidden,shown))
    }
}
