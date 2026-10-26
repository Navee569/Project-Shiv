package com.shiv.assistant.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ProactivePlannerTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    @Test fun eventNudgeDeduplicatedAndRespectsQuietHours() {
        val now = ZonedDateTime.of(2026,10,26,9,0,0,0,zone).toInstant().toEpochMilli()
        val e = AgendaSignal("meeting", "Meet Raj", now + 30*60000L, false)
        val candidate = ProactivePlanner.suggestions(listOf(e,e), now, zone).single()
        assertTrue(ProactivePlanner.shouldDeliver(candidate,now,emptySet(),0,3,zone=zone).shouldNotify)
        assertFalse(ProactivePlanner.shouldDeliver(candidate,now,setOf(candidate.id),0,3,zone=zone).shouldNotify)
        assertFalse(ProactivePlanner.shouldDeliver(candidate,now,emptySet(),3,3,zone=zone).shouldNotify)
        val nighttime = ZonedDateTime.of(2026,10,26,23,0,0,0,zone).toInstant().toEpochMilli()
        val night = ProactivePlanner.suggestions(listOf(e.copy(dueMillis=nighttime+30*60000L)),nighttime,zone).single()
        assertFalse(ProactivePlanner.shouldDeliver(night,nighttime,emptySet(),0,3,zone=zone).shouldNotify)
    }
    @Test fun unverifiedAndCompletedEventsNeverTrigger() {
        val now = ZonedDateTime.of(2026,10,26,9,0,0,0,zone).toInstant().toEpochMilli()
        assertTrue(ProactivePlanner.suggestions(listOf(
            AgendaSignal("a","past",now-600000L,false),
            AgendaSignal("b","done",now+300000L,true),
            AgendaSignal("c","uncertain",now+300000L,false,0.3)),now,zone).isEmpty())
    }
    @Test fun briefWindowsAreClockBounded() {
        assertEquals("morning",ProactivePlanner.briefKind(ZonedDateTime.of(2026,10,26,8,0,0,0,zone)))
        assertEquals("evening",ProactivePlanner.briefKind(ZonedDateTime.of(2026,10,26,19,0,0,0,zone)))
        assertNull(ProactivePlanner.briefKind(ZonedDateTime.of(2026,10,26,2,0,0,0,zone)))
    }
}
