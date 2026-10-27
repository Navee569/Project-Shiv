package com.shiv.assistant.domain

import org.junit.Assert.*
import org.junit.Test

class EnergyBudgetPolicyTest {
    @Test fun unavailableCounterIsNotInvented() {
        assertNull(EnergyBudgetPolicy.usedPercent(null,200))
    }
    @Test fun observedWholeDeviceDropUsesOwnerCap() {
        assertEquals(50,EnergyBudgetPolicy.usedPercent(100_000_000L,200))
        assertEquals(100,EnergyBudgetPolicy.usedPercent(300_000_000L,200))
    }
    @Test fun computeLimitIsIndependent() {
        assertEquals(50,EnergyBudgetPolicy.computePercent(30_000L,60))
    }
}
