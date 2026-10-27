package com.shiv.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class RmsActivityGateTest {
    @Test fun sustainedSoundChangesDutyHintButIsNotACommand() {
        val gate=RmsActivityGate()
        repeat(12) { assertFalse(gate.observe(1f)) }
        assertFalse(gate.observe(7f))
        assertFalse(gate.observe(7f))
        assertTrue(gate.observe(7f))
        gate.reset()
        assertFalse(gate.observe(7f))
        assertFalse(gate.observe(Float.NaN))
    }
}
