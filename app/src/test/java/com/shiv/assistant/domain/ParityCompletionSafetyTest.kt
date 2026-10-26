package com.shiv.assistant.domain

import com.shiv.assistant.awareness.ScreenInspectionPolicy
import com.shiv.assistant.integrations.CalendarCandidate
import com.shiv.assistant.integrations.CalendarImportPolicy
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/** Pure safety assertions for changes based on the audited 8e48ed0 tree. */
class ParityCompletionSafetyTest {
    @Test fun calendarImportRequiresExplicitCleanCandidate() {
        val now = Instant.parse("2026-10-26T10:00:00Z").toEpochMilli()
        val safe = CalendarCandidate("external-91-"+(now+60_000),
            "Doctor appointment",now+60_000,now+3600_000)
        val record = CalendarImportPolicy.accept(safe,now)
        assertNotNull(record)
        assertEquals("owner-reviewed-calendar", record?.source)
        assertNull(CalendarImportPolicy.accept(safe.copy(title="OTP 123456"),now))
        assertNull(CalendarImportPolicy.accept(safe.copy(title="Account number 123456789"),now))
        assertNull(CalendarImportPolicy.accept(safe.copy(id="rogue"),now))
        assertNull(CalendarImportPolicy.accept(safe.copy(startAt=now-5),now))
        assertNull(CalendarImportPolicy.accept(safe.copy(endAt=now-1),now))
        assertNull(CalendarImportPolicy.accept(safe.copy(startAt=now+20L*86400_000L),now))
    }
    @Test fun accessibilityRequiresExactOwnerSourceAndDeniesSensitiveApps() {
        assertFalse(ScreenInspectionPolicy.permits("com.android.calendar",null))
        assertFalse(ScreenInspectionPolicy.permits("com.android.calendar",""))
        assertTrue(ScreenInspectionPolicy.permits("com.android.calendar","com.android.calendar"))
        assertFalse(ScreenInspectionPolicy.permits("com.android.browser","com.android.calendar"))
        assertFalse(ScreenInspectionPolicy.permits("com.phonepe.app","com.phonepe.app"))
        assertFalse(ScreenInspectionPolicy.permits("com.google.android.apps.authenticator2",
            "com.google.android.apps.authenticator2"))
        assertFalse(ScreenInspectionPolicy.permits("com.android.calendar","not a package"))
    }
    @Test fun reminderPermissionNeverRelaxesExternalCommunication() {
        val reminder = ActionProposal(ActionKind.REMINDER,"remind me in 5 minutes",
            Risk.USER_CONFIRMATION,"")
        val granted = ActionAuthorization.decide(reminder,AuthorityTier.ACT_THEN_TELL,false)
        assertTrue(granted.permitted)
        assertFalse(granted.requiresConfirmation)
        val payment = ActionProposal(ActionKind.UPI,"upi://pay",Risk.USER_CONFIRMATION,"")
        val message = ActionProposal(ActionKind.SMS,"hello",Risk.USER_CONFIRMATION,"")
        assertFalse(ActionAuthorization.decide(payment,AuthorityTier.ACT_THEN_TELL,false).permitted)
        assertFalse(ActionAuthorization.decide(message,AuthorityTier.ACT_THEN_TELL,false).permitted)
        assertTrue(ActionAuthorization.decide(message,AuthorityTier.ACT_THEN_TELL,true).permitted)
    }
}
