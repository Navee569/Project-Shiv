package com.shiv.assistant.domain

import org.junit.Assert.*
import org.junit.Test

class UsageSummaryPolicyTest {
    @Test fun hideSensitiveAppsEvenWhenTheyDominateUsage() {
        val sample = listOf(
            AppUsageObservation("com.phonepe.app",5_000_000),
            AppUsageObservation("com.google.android.apps.authenticator2",9_000_000),
            AppUsageObservation("com.example.calendar",900_000),
            AppUsageObservation("com.example.short",30_000))
        assertEquals(listOf("com.example.calendar"),
            UsageSummaryPolicy.allowed(sample).map { it.packageName })
    }
    @Test fun optionalOwnerDenylistAlsoApplies() {
        val sample = listOf(AppUsageObservation("com.example.calendar",900_000))
        assertTrue(UsageSummaryPolicy.allowed(sample,setOf("com.example.calendar")).isEmpty())
    }
}
