package com.shiv.assistant.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class RoutineLearnerTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = ZonedDateTime.of(2026,10,26,10,0,0,0,zone).toInstant().toEpochMilli()
    @Test fun learnsOnlyRepeatedEventsOnDifferentWeeks() {
        val observations = (1..4).map { week ->
            RoutineObservation("e" + week, "Gym", now - week*7L*86400000L)
        }
        val found = RoutineLearner.infer(observations,now,zone).single()
        assertEquals("gym",found.label)
        assertEquals(4,found.evidenceIds.size)
        assertTrue(found.confidence >= 0.75)
    }
    @Test fun duplicateOrSparseEvidenceCannotBecomeFact() {
        val sameDay = (1..4).map { RoutineObservation("duplicate"+it,"Gym",now-it*60000L) }
        assertTrue(RoutineLearner.infer(sameDay,now,zone).isEmpty())
        assertTrue(RoutineLearner.infer(listOf(
            RoutineObservation("a","Gym",now-7*86400000L),
            RoutineObservation("b","Gym",now-14*86400000L)),now,zone).isEmpty())
    }
}
