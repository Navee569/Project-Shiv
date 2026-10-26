package com.shiv.assistant.domain

import org.junit.Assert.*
import org.junit.Test

class ShivSafetyTest {
    @Test fun bankingAndOtpSignalsAreRejectedBeforeStorage() {
        assertTrue(PrivacyPolicy.isDeniedPackage("com.phonepe.app"))
        assertTrue(PrivacyPolicy.isDeniedPackage("com.google.android.apps.authenticator2"))
        assertTrue(PrivacyPolicy.isDeniedPackage("com.example.personal.bank"))
        assertNull(PrivacyPolicy.safeNotificationTitle("com.phonepe.app", "Meeting tomorrow"))
        assertNull(PrivacyPolicy.safeNotificationTitle("com.example.calendar", "Your OTP is 123456"))
        assertEquals("Appointment Thursday", PrivacyPolicy.safeNotificationTitle("com.example.calendar", "Appointment Thursday"))
    }
    @Test fun pausedSensorsAreNotPermitted() {
        assertFalse(PrivacyPolicy.canSense(true, false, false))
        assertFalse(PrivacyPolicy.canSense(false, true, false))
        assertFalse(PrivacyPolicy.canSense(false, false, true))
        assertTrue(PrivacyPolicy.canSense(false, false, false))
    }
    @Test fun urgentRemindersBypassLowBatteryAndHeat() {
        val state = ResourceSnapshot(5, false, 4, 100, false, true)
        val urgent = ResourceGovernor.decide(state, WorkClass.URGENT, PowerMode.LIGHT, 100, 20)
        assertTrue(urgent.allowed)
        assertFalse(urgent.allowLargeModel)
        assertFalse(ResourceGovernor.decide(state, WorkClass.CONVERSATION, PowerMode.FULL, 0, 100).allowed)
    }
    @Test fun largeModelRequiresResourcesAndPower() {
        val good = ResourceSnapshot(80, true, 0, 3600, false, false)
        val lease = ResourceGovernor.decide(good, WorkClass.CONVERSATION, PowerMode.FULL, 0, 600)
        assertTrue(lease.allowed)
        assertTrue(lease.allowLargeModel)
        val poor = ResourceGovernor.decide(good.copy(availableRamMb = 400),
            WorkClass.CONVERSATION, PowerMode.FULL, 0, 600)
        assertTrue(poor.allowed)
        assertFalse(poor.allowLargeModel)
    }
    @Test fun leasesNeverExceedRemainingDailyBudget() {
        val state = ResourceSnapshot(95, true, 0, 4000, false, false)
        val nearLimit = ResourceGovernor.decide(state, WorkClass.CONVERSATION,
            PowerMode.FULL, 599, 600)
        assertTrue(nearLimit.allowed)
        assertEquals(1, nearLimit.maxModelSeconds)
        val capped = ResourceGovernor.decide(state, WorkClass.CONVERSATION,
            PowerMode.FULL, 600, 600)
        assertFalse(capped.allowed)
        assertEquals(0, capped.maxModelSeconds)
        val urgent = ResourceGovernor.decide(state.copy(thermalStatus = 4),
            WorkClass.URGENT, PowerMode.LIGHT, 1000, 600)
        assertTrue(urgent.allowed)
        assertFalse(urgent.allowLargeModel)
    }

    @Test fun modelCannotAuthorizePaymentsOrPrivilegedCommands() {
        listOf("PAYMENT", "PERMANENT_DELETE", "PRIVILEGED", "SEND_MESSAGE").forEach {
            val policy = AuthorityPolicy.authorize(it, AuthorityTier.FREE, true, false)
            assertEquals(ActionAuthority.NEEDS_CONFIRMATION, policy.decision)
            assertFalse(AuthorityPolicy.maySuggestPromotion(it, 999))
        }
        assertEquals(ActionAuthority.NOT_ALLOWED,
            AuthorityPolicy.authorize("OPEN_APP", AuthorityTier.FREE, false, true).decision)
    }
    @Test fun interruptionRulesApplyToRoutinePredictions() {
        val nudge = NudgeCandidate("school", 70, 10, 0.92)
        val base = InterruptionContext(false, 0, 3, emptySet(), true)
        assertTrue(Anticipation.decide(nudge, base).shouldNotify)
        assertFalse(Anticipation.decide(nudge, base.copy(quietHours = true)).shouldNotify)
        assertFalse(Anticipation.decide(nudge, base.copy(todayCount = 3)).shouldNotify)
        assertFalse(Anticipation.decide(nudge, base.copy(recentlyNotifiedIds = setOf("school"))).shouldNotify)
        assertFalse(Anticipation.decide(nudge.copy(confidence = 0.4), base).shouldNotify)
    }
}
