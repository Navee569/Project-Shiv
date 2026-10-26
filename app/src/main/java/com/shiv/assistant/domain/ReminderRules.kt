package com.shiv.assistant.domain

import java.time.LocalTime

sealed class ReminderRuleCommand {
    data class SilenceBefore(val minuteOfDay: Int) : ReminderRuleCommand()
    object Clear : ReminderRuleCommand()
}

/**
 * Deterministic owner rule, not model reasoning. "Ignore reminders before 9 AM"
 * must affect *later* reminders, including pending WorkManager deliveries.
 */
object ReminderRules {
    private val silencePattern = Regex(
        """(?i)^(?:ignore (?:all )?reminders?|don't remind me|do not remind me|no reminders?) before ([0-9]{1,2})(?::([0-9]{2}))?\s*(am|pm)[.!]?$"""
    )
    fun parse(text: String): ReminderRuleCommand? {
        val input = text.trim()
        if (input.matches(Regex("""(?i)^(?:remove reminder rule|allow reminders anytime|clear reminder rule)[.!]?$""")))
            return ReminderRuleCommand.Clear
        val match = silencePattern.matchEntire(input) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].ifEmpty { "0" }.toIntOrNull() ?: return null
        if (hour !in 1..12 || minute !in 0..59) return null
        val h24 = (hour % 12) + if (match.groupValues[3].equals("pm", true)) 12 else 0
        return ReminderRuleCommand.SilenceBefore(h24 * 60 + minute)
    }
    fun shouldSuppress(now: LocalTime, earliestMinuteOfDay: Int?): Boolean {
        if (earliestMinuteOfDay == null || earliestMinuteOfDay !in 0..1439) return false
        return now.hour * 60 + now.minute < earliestMinuteOfDay
    }
    fun format(minuteOfDay: Int): String {
        require(minuteOfDay in 0..1439)
        val h = minuteOfDay / 60
        val m = minuteOfDay % 60
        val meridiem = if (h >= 12) "PM" else "AM"
        return (if (h % 12 == 0) 12 else h % 12).toString() +
            ":" + m.toString().padStart(2, '0') + " " + meridiem
    }
}
