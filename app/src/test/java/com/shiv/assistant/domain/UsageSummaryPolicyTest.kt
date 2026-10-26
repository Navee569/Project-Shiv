package com.shiv.assistant.domain

import org.junit.Assert.*
import org.junit.Test

class UsageSummaryPolicyTest {
    @Test fun privateAppsAreFilteredBeforeDisplayingUsage() {
        val records=listOf(
            UsageObservation("com.phonepe.app",5_000_000),
            UsageObservation("com.microsoft.authenticator",8_000_000),
            UsageObservation("com.example.calendar",900_000),
            UsageObservation("com.example.small",30_000))
        assertEquals(listOf("com.example.calendar"),
            UsageSummaryPolicy.filtered(records).map { it.packageName })
        assertTrue(UsageSummaryPolicy.filtered(records,
            setOf("com.example.calendar")).isEmpty())
    }
}
