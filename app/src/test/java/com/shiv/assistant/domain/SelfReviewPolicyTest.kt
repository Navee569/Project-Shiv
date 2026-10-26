package com.shiv.assistant.domain

import org.junit.Assert.*
import org.junit.Test

class SelfReviewPolicyTest {
    @Test fun repeatedNegativeFeedbackReducesOnlyOptionalInterruptions() {
        assertEquals(2, SelfReviewPolicy.suggestedDailyCap(listOf(
            ReviewReaction.DISMISSED,ReviewReaction.CORRECTED,ReviewReaction.DISMISSED)))
        assertEquals(3, SelfReviewPolicy.suggestedDailyCap(listOf(
            ReviewReaction.ACTED,ReviewReaction.SNOOZED)))
        assertEquals(1, SelfReviewPolicy.suggestedDailyCap(listOf(
            ReviewReaction.CORRECTED, ReviewReaction.CORRECTED, ReviewReaction.CORRECTED),1))
    }
}
