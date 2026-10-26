package com.shiv.assistant.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

data class DueDateProposal(val date:LocalDate,val title:String)
/** A text-recognition hypothesis, never an authorized event. */
object OcrDueDatePolicy {
    private val pattern=Regex(
        "(?i)\\b(?:due(?:\\s+date)?|deadline|pay by)\\s*[:\\-]?\\s*(\\d{4}-\\d{2}-\\d{2})\\b")
    private val formatter=DateTimeFormatter.ofPattern("uuuu-MM-dd")
        .withResolverStyle(ResolverStyle.STRICT)
    fun propose(text:String,now:LocalDate=LocalDate.now()):DueDateProposal? {
        val match=pattern.find(text.take(2_000))?:return null
        val date=runCatching {LocalDate.parse(match.groupValues[1],formatter)}.getOrNull()
            ?:return null
        if(date.isBefore(now)||date.isAfter(now.plusYears(1)))return null
        return DueDateProposal(date,"Review document commitment")
    }
}
