package com.shiv.assistant.domain

import java.time.Instant
import java.time.ZoneId
import kotlin.math.pow

data class PatternCandidate(val id:String,val hypothesis:String,val evidence:String,
    val confidence:Double,val lastSeenAt:Long)

/** Only coarse source and time metadata become hypotheses; signal text is ignored. */
object PatternPolicy {
    private val labels=mapOf(
        "app_open" to "open Shiv",
        "long_idle_unlock" to "unlock after a long idle",
        "task_deadline" to "have a task deadline",
        "call-end" to "finish a call",
        "calendar-change" to "change a calendar item"
    )
    fun candidates(observations:List<Pair<String,Long>>,now:Long,
        zone:ZoneId=ZoneId.systemDefault()):List<PatternCandidate> {
        val recent=observations.filter { (_,at) -> at in now-30L*86_400_000L..now }
            .mapNotNull { (source,at) ->
                val label=labels[source] ?: return@mapNotNull null
                val dateTime=Instant.ofEpochMilli(at).atZone(zone)
                val slot=when(dateTime.hour) { in 5..11->"morning";in 12..17->"afternoon";else->"evening" }
                Triple(source to slot,dateTime.toLocalDate(),at) to label
            }
        return recent.groupBy { it.first.first }.mapNotNull { (key,rows) ->
            val days=rows.map { it.first.second }.distinct().size
            if(days<3)return@mapNotNull null
            val latest=rows.maxOf { it.first.third }
            val ageDays=((now-latest)/86_400_000L).coerceAtLeast(0L)
            val decay=0.92.pow(ageDays.toDouble())
            val confidence=((0.45+days*0.07).coerceAtMost(0.85)*decay).coerceIn(0.0,0.85)
            if(confidence<0.5)return@mapNotNull null
            val id="pattern-${key.first}-${key.second}"
            PatternCandidate(id,"You often ${rows.first().second} in the ${key.second}",
                "Observed on $days distinct days in the past 30 days",confidence,latest)
        }.sortedBy { it.id }
    }
}
