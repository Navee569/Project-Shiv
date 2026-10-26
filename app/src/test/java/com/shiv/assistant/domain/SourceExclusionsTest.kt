package com.shiv.assistant.domain

import com.shiv.assistant.awareness.ScreenInspectionPolicy
import org.junit.Assert.*
import org.junit.Test

class SourceExclusionsTest {
    @Test fun customCaseInsensitiveBlocklistAppliesBeforeScreenCapture() {
        val owner=SourceExclusions.fromString("Com.Example.Calendar\ncom.example.meeting\nwrong package\n")
        assertEquals(2,owner.size)
        assertTrue(SourceExclusions.excluded("com.example.CALENDAR",owner))
        assertTrue(SourceExclusions.excluded("com.PHONEPE.app",emptySet()))
        assertFalse(ScreenInspectionPolicy.permits("com.example.calendar",
            "com.example.calendar",owner))
        assertTrue(ScreenInspectionPolicy.permits("com.example.allowed",
            "com.example.allowed",owner))
    }
    @Test fun malformedPackagesCannotBeStoredAsAllowOrDenyRules() {
        listOf("","justname","../..","x@com","a.b\ncom.bad.package").forEach {
            assertFalse(SourceExclusions.validPackage(it))
        }
        assertTrue(SourceExclusions.validPackage("com.example.calendar"))
    }
}
