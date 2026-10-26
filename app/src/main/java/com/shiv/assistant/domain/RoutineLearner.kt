package com.shiv.assistant.domain

import java.time.Instant
import java.time.ZoneId

data class RoutineObservation(val id: String, val label: String, val occurredAt: Long)
data class LearnedRoutine(val label: String, val weekday: Int, val hour: Int,
    val evidenceIds: List<String>, val confidence: Double)

/** Hypotheses from repeated owner-stored events; never a verified owner fact. */
object RoutineLearner {
    fun infer(observations: List<RoutineObservation>, now: Long,
        zone: ZoneId = ZoneId.systemDefault()): List<LearnedRoutine> {
        val cutoff = now - 90L * 86400000
        return observations.asSequence().filter { it.occurredAt in cutoff..now }
            .filter { it.label.trim().length in 3..120 }
            .groupBy { item ->
                val whenLocal = Instant.ofEpochMilli(item.occurredAt).atZone(zone)
                Triple(item.label.trim().lowercase(), whenLocal.dayOfWeek.value,
                    whenLocal.hour / 2 * 2)
            }.mapNotNull { (key, values) ->
                val distinctDays = values.distinctBy {
                    Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate()
                }.sortedBy { it.occurredAt }
                if (distinctDays.size < 3) return@mapNotNull null
                val span = distinctDays.last().occurredAt - distinctDays.first().occurredAt
                if (span < 13L * 86400000) return@mapNotNull null
                LearnedRoutine(key.first, key.second, key.third,
                    distinctDays.takeLast(8).map { it.id },
                    (0.72 + 0.04 * (distinctDays.size - 3)).coerceAtMost(0.92))
            }.sortedByDescending { it.confidence }.take(20).toList()
    }
}
