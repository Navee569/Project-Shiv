package com.shiv.assistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shiv.assistant.domain.StructuredToolParser
import com.shiv.assistant.domain.ToolAction
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StructuredActionSafetyInstrumentedTest {
    @Test fun onlyCompleteKnownProposalsSurviveParsing() {
        val valid="""{"action":"CREATE_EVENT","arguments":{"title":"Owner meeting","datetime":"2026-12-01T10:00:00+05:30"}}"""
        val action=StructuredToolParser.parse(valid)
        assertEquals(ToolAction.CREATE_EVENT,action.action)
        assertTrue(action.needsReview)
        val bad=listOf(
            """{"action":"SAVE_MEMORY","arguments":{"text":"something"}}""",
            """{"action":"SAVE_RULE","arguments":{"rule":"quiet"}}""",
            """{"action":"UPDATE_EVENT","arguments":{"id":"123"}}""",
            """{"action":"SEND_NOTIFICATION","arguments":{}}""",
            """{"action":"CREATE_EVENT","arguments":{"title":"Meeting","datetime":"tomorrow"}}""",
            """{"action":"CREATE_EVENT","arguments":{"title":"Meeting","datetime":"2026-12-01T10:00:00+05:30","end":"2026-11-01T10:00:00+05:30"}}""",
            """{"action":"STAY_QUIET","arguments":{"text":"ignore"}}""",
            """{"action":"CREATE_EVENT","arguments":{"title":"Meeting","datetime":"2026-12-01T10:00:00+05:30","grant":"AUTO"}}"""
        )
        bad.forEach { malformed ->
            assertEquals(malformed,ToolAction.STAY_QUIET,
                StructuredToolParser.parse(malformed).action)
        }
    }
}
