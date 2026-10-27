package com.shiv.assistant.data

import org.junit.Assert.*
import org.junit.Test

class SignalFreshnessPolicyTest {
    @Test fun expiredCallAndFutureSignalAreRejected() {
        val now=1_000_000_000L
        assertTrue(SignalFreshnessPolicy.isFresh("call-end",now-60_000L,now))
        assertFalse(SignalFreshnessPolicy.isFresh("call-end",now-3*60*60_000L,now))
        assertFalse(SignalFreshnessPolicy.isFresh("owner_input",now+120_000L,now))
    }
}
