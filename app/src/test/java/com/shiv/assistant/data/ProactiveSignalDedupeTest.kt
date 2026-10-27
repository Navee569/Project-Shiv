package com.shiv.assistant.data

import org.junit.Assert.*
import org.junit.Test

class ProactiveSignalDedupeTest {
    @Test fun onlyCoarseTriggerFactsAndBlandNotificationsMayMerge() {
        assertTrue(SignalDedupePolicy.mayMerge(SignalRecord("1","app_open","Owner opened Shiv",2)))
        assertTrue(SignalDedupePolicy.mayMerge(SignalRecord("2","task_deadline","Task deadline 42",3)))
        assertFalse(SignalDedupePolicy.mayMerge(SignalRecord("3","owner_input","Meet on Monday",2)))
        assertFalse(SignalDedupePolicy.mayMerge(SignalRecord("4","notification:app","Meeting tomorrow",2)))
    }
}
