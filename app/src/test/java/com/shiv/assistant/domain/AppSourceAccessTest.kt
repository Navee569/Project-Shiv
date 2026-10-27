package com.shiv.assistant.domain

import org.junit.Assert.*
import org.junit.Test

class AppSourceAccessTest {
    @Test fun notificationOnlyNeverAllowsScreenFacts() {
        val modes=mapOf("com.example.calendar" to AppSourceMode.NOTIFICATIONS_ONLY)
        assertTrue(AppSourceAccess.permits("com.example.calendar","notification",modes))
        assertFalse(AppSourceAccess.permits("com.example.calendar","accessibility",modes))
        assertFalse(AppSourceAccess.permits("com.example.calendar","unknown",modes))
        assertTrue(AppSourceAccess.permits("com.example.other","accessibility",modes))
        assertFalse(AppSourceAccess.permits("com.phonepe.app","notification",
            mapOf("com.phonepe.app" to AppSourceMode.INCLUDE)))
    }

    @Test fun encryptedValueCodecRejectsMalformedOrDuplicatePolicies() {
        val modes=mapOf("com.example.b" to AppSourceMode.EXCLUDE,
            "com.example.a" to AppSourceMode.NOTIFICATIONS_ONLY)
        assertEquals(modes,AppSourceAccess.decode(AppSourceAccess.encode(modes)))
        assertTrue(runCatching {
            AppSourceAccess.decode("com.example.a=INCLUDE\ncom.example.a=EXCLUDE")
        }.isFailure)
        assertTrue(runCatching { AppSourceAccess.decode("bad package=INCLUDE") }.isFailure)
        assertTrue(runCatching { AppSourceAccess.decode("com.example.a=UNKNOWN") }.isFailure)
        assertTrue(runCatching { AppSourceAccess.decode("   ") }.isFailure)
    }
}
