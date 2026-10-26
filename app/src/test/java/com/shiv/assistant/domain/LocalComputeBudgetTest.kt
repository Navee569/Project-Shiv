package com.shiv.assistant.domain

import org.junit.Assert.*
import org.junit.Test

class LocalComputeBudgetTest {
    @Test fun neverExceedsOwnerDailyAllowance() {
        assertEquals(60,LocalComputeBudget.remainingSeconds(0,60))
        assertEquals(59,LocalComputeBudget.remainingSeconds(1,60))
        assertEquals(0,LocalComputeBudget.remainingSeconds(60_000,60))
        assertEquals(0,LocalComputeBudget.remainingSeconds(Long.MAX_VALUE,60))
        assertEquals(13,LocalComputeBudget.leaseSeconds(47_000,60,60))
        assertEquals(12,LocalComputeBudget.leaseSeconds(47_000,60,12))
        assertEquals(0,LocalComputeBudget.leaseSeconds(60_000,60,30))
    }
    @Test fun invalidBudgetIsRejected() {
        assertTrue(runCatching { LocalComputeBudget.remainingSeconds(0,59) }.isFailure)
        assertTrue(runCatching { LocalComputeBudget.remainingSeconds(0,3601) }.isFailure)
    }
}
