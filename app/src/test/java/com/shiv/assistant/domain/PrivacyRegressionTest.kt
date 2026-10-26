package com.shiv.assistant.domain

import org.junit.Assert.*
import org.junit.Test

class PrivacyRegressionTest {
    @Test fun caseVariantPackagesCannotBypassIndiaDenylist() {
        assertTrue(PrivacyPolicy.isDeniedPackage("com.sbi.SBIFreedomPlus"))
        assertTrue(PrivacyPolicy.isDeniedPackage("com.amazon.mShop.android.shopping"))
        assertTrue(PrivacyPolicy.isDeniedPackage("com.microsoft.msa.authenticator"))
        assertTrue(PrivacyPolicy.isDeniedPackage("com.example.private", setOf("COM.EXAMPLE.PRIVATE")))
        assertNull(PrivacyPolicy.safeNotificationTitle("com.sbi.SBIFreedomPlus", "Meet tomorrow"))
    }
    @Test fun screenFieldsAndSecureScreensFailClosed() {
        assertFalse(PrivacyPolicy.mayReadScreen("com.phonepe.app", false, false))
        assertFalse(PrivacyPolicy.mayReadScreen("com.example.notes", true, false))
        assertFalse(PrivacyPolicy.mayReadScreen("com.example.notes", false, true))
        assertTrue(PrivacyPolicy.mayReadScreen("com.example.notes", false, false))
    }
}
