package com.shiv.assistant.domain

import org.junit.Assert.*
import org.junit.Test

class MalformedActionPolicyTest {
    @Test fun authorityFloorCannotBeLoweredForMessagesOrRestrictedActions() {
        for(kind in listOf(ActionKind.UPI,ActionKind.SMS,ActionKind.EMAIL,
            ActionKind.WHATSAPP,ActionKind.TELEGRAM)) {
            val proposal=ActionProposal(kind,"redacted",Risk.USER_CONFIRMATION,"")
            assertEquals(listOf(AuthorityTier.ASK_FIRST),ActionAuthorization.editableTiers(kind))
            assertFalse(ActionAuthorization.decide(proposal,AuthorityTier.FREE,false).permitted)
        }
        assertFalse(ActionAuthorization.decide(ActionProposal(ActionKind.NOTES,"x",
            Risk.RESTRICTED,""),AuthorityTier.FREE,true).permitted)
    }
    @Test fun everySupportedKindHasAnOwnerVisibleTierAndRecordedOutcome() {
        ActionKind.entries.filter { it!=ActionKind.UNKNOWN }.forEach { kind ->
            assertTrue(ActionAuthorization.editableTiers(kind).isNotEmpty())
            val proposal=ActionProposal(kind,"[REDACTED]",Risk.USER_CONFIRMATION,"")
            val tier=ActionAuthorization.editableTiers(kind).first()
            val decision=ActionAuthorization.decide(proposal,tier,true)
            val log=ActionAuthorization.log(proposal,decision,"OWNER_APPROVED","TEST_RESULT")
            assertEquals("[REDACTED]",log.argsRedacted)
            assertTrue(log.tier.isNotBlank() && log.permission.isNotBlank() &&
                log.result.isNotBlank() && log.undo=="NOT_AVAILABLE")
        }
    }
}
