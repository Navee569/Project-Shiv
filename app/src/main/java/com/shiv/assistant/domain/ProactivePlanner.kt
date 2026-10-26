package com.shiv.assistant.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Pure, explainable event guidance. Inputs are owner-stored agenda, not model guesses. */
data class AgendaSignal(val id: String, val title: String, val dueMillis: Long,
    val completed: Boolean, val confidence: Double = 1.0)
data class ProactiveSuggestion(val id: String, val title: String, val reason: String,
    val importance: Int, val dueMillis: Long)

object ProactivePlanner {
    fun suggestions(events: List<AgendaSignal>, now: Long,
        zone: ZoneId = ZoneId.systemDefault()): List<ProactiveSuggestion> {
        val localToday = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return events.asSequence().filter { !it.completed && it.confidence >= 0.75 }
            .filter { it.title.isNotBlank() }
            .mapNotNull { e ->
                val minutes = (e.dueMillis - now) / 60000L
                val day = Instant.ofEpochMilli(e.dueMillis).atZone(zone).toLocalDate()
                when {
                    minutes in 0..90 -> ProactiveSuggestion("upcoming:" + e.id,
                        "Upcoming: " + e.title.take(100), "Owner's saved event due within 90 minutes",
                        70, e.dueMillis)
                    day == localToday.plusDays(1) && minutes in 540..2160 ->
                        ProactiveSuggestion("prepare:" + e.id,
                            "Tomorrow: " + e.title.take(100),
                            "Owner's saved event tomorrow; optional preparation", 60, e.dueMillis)
                    else -> null
                }
            }.distinctBy { it.id }.take(20).toList()
    }

    fun shouldDeliver(candidate: ProactiveSuggestion, now: Long,
        lastSent: Set<String>, todayCount: Int, cap: Int,
        quietStartHour: Int = 22, quietEndHour: Int = 8,
        zone: ZoneId = ZoneId.systemDefault()): NudgeDecision {
        val hour = Instant.ofEpochMilli(now).atZone(zone).hour
        val quiet = if (quietStartHour > quietEndHour)
            hour >= quietStartHour || hour < quietEndHour
        else hour >= quietStartHour && hour < quietEndHour
        return Anticipation.decide(
            NudgeCandidate(candidate.id, candidate.importance,
                ((candidate.dueMillis - now) / 60000L).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(), 1.0),
            InterruptionContext(quiet, todayCount, cap, lastSent, true))
    }

    fun briefKind(now: ZonedDateTime): String? = when (now.hour) {
        in 7..10 -> "morning"
        in 18..21 -> "evening"
        else -> null
    }
}
