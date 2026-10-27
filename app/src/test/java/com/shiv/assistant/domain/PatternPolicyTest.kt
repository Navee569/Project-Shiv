package com.shiv.assistant.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class PatternPolicyTest {
    private val zone=ZoneId.of("Asia/Kolkata")
    private val now=ZonedDateTime.of(2026,10,27,12,0,0,0,zone).toInstant().toEpochMilli()
    @Test fun requiresThreeDistinctRecentDaysAndIgnoresSignalTextSources() {
        val observations=(1L..3L).map { day ->
            "app_open" to (now-day*86_400_000L-2*60*60_000L)
        }+listOf("owner_input" to now,"app_open" to now-86_400_000L-2*60*60_000L)
        val result=PatternPolicy.candidates(observations,now,zone)
        assertEquals(1,result.size)
        assertEquals("pattern-app_open-morning",result.single().id)
        assertTrue(result.single().confidence>=0.5)
        assertTrue(PatternPolicy.candidates(observations.take(2),now,zone).isEmpty())
    }
    @Test fun oldObservationsDecayOut() {
        val observations=(20L..22L).map { "call-end" to (now-it*86_400_000L) }
        assertTrue(PatternPolicy.candidates(observations,now,zone).isEmpty())
    }
}
