package com.shiv.assistant.domain

import com.shiv.assistant.automation.CommitmentParser
import com.shiv.assistant.automation.CommitmentProposal
import com.shiv.assistant.automation.ShivReminders
import com.shiv.assistant.ai.InferencePriority
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class PhaseOnePolicyTest {
    @Test fun bankingDenylist() {
        listOf("com.phonepe.app","com.google.android.apps.nbu.paisa.user",
            "com.microsoft.authenticator","com.x8bit.bitwarden",
            "com.example.creditcard.wallet").forEach {
            assertTrue(SensitiveAppDenylist.excluded(it))
        }
        assertFalse(SensitiveAppDenylist.excluded("com.android.calendar"))
        assertTrue(SensitiveAppDenylist.excluded("com.example.news",setOf("com.example.news")))
    }
    @Test fun paymentHasHardApprovalFloor() {
        val money=ActionProposal(ActionKind.UPI,"500",Risk.USER_CONFIRMATION,"Review")
        val decision=ActionAuthorization.decide(money,AuthorityTier.FREE,false)
        assertEquals(AuthorityTier.ASK_FIRST,decision.tier)
        assertFalse(decision.permitted)
        assertTrue(ActionAuthorization.decide(money,AuthorityTier.FREE,true).permitted)
    }
    @Test fun unknownActionsFailClosed() {
        assertFalse(ActionAuthorization.decide(
            ActionProposal(ActionKind.UNKNOWN,"invalid",Risk.READ_ONLY,""),
            AuthorityTier.FREE,true).permitted)
    }
    @Test fun configuredActionTier() {
        val action=ActionProposal(ActionKind.MAPS,"airport",Risk.USER_CONFIRMATION,"")
        assertEquals(AuthorityTier.ACT_THEN_TELL,
            ActionAuthorization.decide(action,AuthorityTier.ACT_THEN_TELL,false).tier)
    }
    @Test fun quietHoursSuppressNotification() {
        val now=ZonedDateTime.of(2026,10,25,23,30,0,0,ZoneId.of("Asia/Kolkata"))
        val input=ProactiveInputs(urgency=5,relevance=0.96,confidence=0.9,
            minutesToEvent=15,now=now,interruptionsToday=0)
        assertEquals(ProactiveOutcome.STAY_QUIET,ProactivePolicy.decide(input).outcome)
        assertEquals(ProactiveOutcome.STAY_QUIET,ProactivePolicy.decide(
            input.copy(alreadyNotified=true)).outcome)
    }
    @Test fun urgentReminderPassesLowPower() {
        val now=ZonedDateTime.of(2026,10,25,23,30,0,0,ZoneId.of("Asia/Kolkata"))
        val input=ProactiveInputs(urgency=10,relevance=0.95,confidence=0.98,
            minutesToEvent=4,now=now,interruptionsToday=100,batteryLow=true)
        assertEquals(ProactiveOutcome.NOTIFY,ProactivePolicy.decide(input).outcome)
        assertTrue(PowerPolicy.allow(InferencePriority.URGENT,PowerMode.LIGHT))
        assertFalse(PowerPolicy.allow(InferencePriority.BACKGROUND,PowerMode.LIGHT))
    }
    @Test fun powerModesHaveDefinedRules() {
        assertEquals(PowerMode.FULL,PowerPolicy.mode(PowerContext(75,true,false,0)))
        assertEquals(PowerMode.LIGHT,PowerPolicy.mode(PowerContext(15,false,false,0)))
        assertEquals(PowerMode.LIGHT,PowerPolicy.mode(PowerContext(75,true,true,0)))
    }
    @Test fun ambiguousCommitmentRequestsClarification() {
        val zone=ZoneId.of("Asia/Kolkata")
        val now=ZonedDateTime.of(2026,10,25,11,0,0,0,zone)
        assertTrue(CommitmentParser.analyze("I told Raj I'd meet him at 5 on Thursday",zone,now)
            is CommitmentProposal.Clarify)
    }
    @Test fun explicitTimeProducesReviewableCandidate() {
        val zone=ZoneId.of("Asia/Kolkata")
        val now=ZonedDateTime.of(2026,10,25,11,0,0,0,zone)
        assertTrue(CommitmentParser.analyze("meet Raj Thursday 5 pm",zone,now)
            is CommitmentProposal.Ready)
    }
    @Test fun reminderParsingBoundaries() {
        assertEquals(600L,ShivReminders.parseSeconds("remind me in 10 minutes to stretch"))
        assertEquals(7200L,ShivReminders.parseSeconds("remind in 2 hours"))
        assertNull(ShivReminders.parseSeconds("remind in 10000 days"))
    }
}
