package com.shiv.assistant.vision

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

data class OcrTaskCandidate(val dueDate: LocalDate, val title: String)

/** Conservative, locale-unambiguous dates only; owner supplies time and approves. */
object OcrTaskParser {
    private val duePattern = Regex(
        "(?i)\\b(?:due(?:\\s+date)?|deadline|pay by)\\s*[:\\-]?\\s*(\\d{4}-\\d{2}-\\d{2})\\b")
    private val strict = DateTimeFormatter.ofPattern("uuuu-MM-dd")
        .withResolverStyle(ResolverStyle.STRICT)
    fun parse(text: String, today: LocalDate = LocalDate.now()): OcrTaskCandidate? {
        val found = duePattern.find(text.take(2000)) ?: return null
        val day = runCatching { LocalDate.parse(found.groupValues[1], strict) }.getOrNull()
            ?: return null
        if (day.isBefore(today) || day.isAfter(today.plusYears(1))) return null
        return OcrTaskCandidate(day, "Review document due item")
    }
}
