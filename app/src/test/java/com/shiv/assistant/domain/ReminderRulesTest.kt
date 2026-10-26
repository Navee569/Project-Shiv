package com.shiv.assistant.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

class ReminderRulesTest {
    @Test fun storesAndAppliesExplicitOwnerRule() {
        val rule = ReminderRules.parse("Ignore reminders before 9 AM") as ReminderRuleCommand.SilenceBefore
        assertEquals(540, rule.minuteOfDay)
        assertTrue(ReminderRules.shouldSuppress(LocalTime.of(8, 59), rule.minuteOfDay))
        assertFalse(ReminderRules.shouldSuppress(LocalTime.of(9, 0), rule.minuteOfDay))
        assertFalse(ReminderRules.shouldSuppress(LocalTime.NOON, rule.minuteOfDay))
        assertEquals("9:00 AM", ReminderRules.format(rule.minuteOfDay))
    }
    @Test fun rejectsMalformedClockAndCanClearRule() {
        assertNull(ReminderRules.parse("Ignore reminders before 13 AM"))
        assertNull(ReminderRules.parse("Ignore reminders before 9:65 AM"))
        assertTrue(ReminderRules.parse("Allow reminders anytime") is ReminderRuleCommand.Clear)
        assertEquals(0, (ReminderRules.parse("Don't remind me before 12 AM")
            as ReminderRuleCommand.SilenceBefore).minuteOfDay)
        assertEquals(720, (ReminderRules.parse("no reminders before 12 pm")
            as ReminderRuleCommand.SilenceBefore).minuteOfDay)
    }
}
