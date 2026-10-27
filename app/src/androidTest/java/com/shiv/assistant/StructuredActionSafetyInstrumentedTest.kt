package com.shiv.assistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shiv.assistant.domain.StructuredToolParser
import com.shiv.assistant.domain.ToolAction
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StructuredActionSafetyInstrumentedTest {
    @Test fun everySupportedContractParsesForOwnerReview() {
        val proposals=mapOf(
            ToolAction.CREATE_EVENT to """{"title":"Meeting","datetime":"2026-12-01T10:00:00+05:30"}""",
            ToolAction.UPDATE_EVENT to """{"id":"event-1","title":"New title"}""",
            ToolAction.SET_REMINDER to """{"title":"Take medicine","datetime":"2026-12-01T10:00:00+05:30"}""",
            ToolAction.SAVE_MEMORY to """{"text":"I prefer tea","category":"FACT"}""",
            ToolAction.SAVE_RULE to """{"rule":"reminders_minute_of_day","value":"540"}""",
            ToolAction.ASK_USER to """{"question":"Which day?"}""",
            ToolAction.SEND_NOTIFICATION to """{"text":"Time to leave"}"""
        )
        proposals.forEach { (kind,args) ->
            val result=StructuredToolParser.parseOrNull("""{"action":"${kind.name}","arguments":$args}""")
            assertEquals(kind,result?.action)
            assertTrue(result?.needsReview==true)
        }
        assertEquals(ToolAction.STAY_QUIET,StructuredToolParser.parseOrNull(
            """{"action":"STAY_QUIET","arguments":{}}""")?.action)
    }
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
            """{"action":"CREATE_EVENT","arguments":{"title":"Meeting","datetime":"2026-12-01T10:00:00+05:30","grant":"AUTO"}}""",
            "{}",
            """{"action":"SAVE_RULE","arguments":{"rule":"reminders_minute_of_day","value":"1440"}}""",
            """{"action":"SEND_NOTIFICATION","arguments":{"text":"hello","permission":"FREE"}}""",
            """{"action":"PAY","arguments":{}}"""
        )
        bad.forEach { malformed ->
            assertEquals(malformed,ToolAction.STAY_QUIET,
                StructuredToolParser.parse(malformed).action)
        }
    }
}
