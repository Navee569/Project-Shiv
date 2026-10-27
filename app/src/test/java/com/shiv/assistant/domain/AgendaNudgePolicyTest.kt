package com.shiv.assistant.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AgendaNudgePolicyTest {
    private val zone=ZoneId.of("Asia/Kolkata")
    private fun local(hour:Int)=ZonedDateTime.of(2026,10,26,hour,0,0,0,zone)
        .toInstant().toEpochMilli()

    @Test fun ownerEventsRespectQuietHoursCapAndDeduplication() {
        val now=local(10)
        val candidate=AgendaNudge("approved-event:42",now+45*60_000L)
        assertTrue(AgendaNudgePolicy.decide(candidate,now,emptySet(),0,3,zone).deliver)
        assertFalse(AgendaNudgePolicy.decide(candidate,now,setOf(candidate.key),0,3,zone).deliver)
        assertFalse(AgendaNudgePolicy.decide(candidate,now,emptySet(),3,3,zone).deliver)
        val evening=local(22)
        assertFalse(AgendaNudgePolicy.decide(
            candidate.copy(dueAt=evening+30*60_000L),evening,emptySet(),0,3,zone).deliver)
        assertFalse(AgendaNudgePolicy.decide(
            candidate.copy(dueAt=now+180*60_000L),now,emptySet(),0,3,zone).deliver)
        assertFalse(AgendaNudgePolicy.decide(
            candidate.copy(dueAt=now-1),now,emptySet(),0,3,zone).deliver)
    }
    @Test fun negativeOwnerReactionsCannotIncreaseInterruptions() {
        assertEquals(3,AgendaNudgePolicy.limitFromFeedback(listOf("ACTED","SNOOZED")))
        assertEquals(2,AgendaNudgePolicy.limitFromFeedback(listOf(
            "DISMISSED","CORRECTED","DISMISSED")))
        assertEquals(1,AgendaNudgePolicy.limitFromFeedback(
            listOf("DISMISSED","DISMISSED","DISMISSED"),1))
    }
    @Test fun reactionsSuppressOrAllowOneDelayedRepeat() {
        val now=local(10)
        assertNull(AgendaNudgePolicy.deliveryKey("agenda:one","DISMISSED",now-1000,now))
        assertNull(AgendaNudgePolicy.deliveryKey("agenda:one","CORRECTED",now-1000,now))
        assertNull(AgendaNudgePolicy.deliveryKey("agenda:one","SNOOZED",now-14*60_000L,now))
        assertEquals("agenda:one:snooze",AgendaNudgePolicy.deliveryKey(
            "agenda:one","SNOOZED",now-15*60_000L,now))
    }
}
