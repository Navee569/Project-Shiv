package com.shiv.assistant.ai

import org.junit.Assert.*
import org.junit.Test

class ModelResidencyPolicyTest {
    @Test fun coResidencyNeedsConservativeHeadroom() {
        assertFalse(ModelResidencyPolicy.canCoexist(3_999_999_999L))
        assertTrue(ModelResidencyPolicy.canCoexist(4_000_000_000L))
    }
    @Test fun slowFallbackRequiresMeasuredReply() {
        assertFalse(ModelResidencyPolicy.slowConversation(null))
        assertFalse(ModelResidencyPolicy.slowConversation(12_000L))
        assertTrue(ModelResidencyPolicy.slowConversation(12_001L))
    }
}
