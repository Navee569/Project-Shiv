package com.shiv.assistant.domain

import org.junit.Assert.*
import org.junit.Test

class StructuredActionsTest {
    @Test fun onlyKnownToolsAndExactArgumentsAccepted() {
        assertTrue(StructuredActions.parse("""{"tool":"create_event","args":{"title":"Meet Raj","starts_at":"2026-10-29T17:00"}}""")
            is BrainTool.Proposed)
        assertTrue(StructuredActions.parse("""{"tool":"delete_everything","args":{}}""") is BrainTool.Quiet)
        assertTrue(StructuredActions.parse("""{"tool":"save_memory","args":{"fact":"x","tier":"FREE"}}""") is BrainTool.Quiet)
        assertTrue(StructuredActions.parse("""{"tool":"set_reminder","args":{"text":42,"when":"now"}}""") is BrainTool.Quiet)
        assertTrue(StructuredActions.parse("not-json") is BrainTool.Quiet)
    }
}
