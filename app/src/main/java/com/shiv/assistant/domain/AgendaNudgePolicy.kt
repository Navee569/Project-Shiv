package com.shiv.assistant.domain

import java.time.Instant
import java.time.ZoneId

/** An optional notification candidate based ONLY on an existing owner-approved event. */
data class AgendaNudge(val key: String, val dueAt: Long)
data class AgendaNudgeDecision(val deliver: Boolean, val reason: String)

/** Pure, deterministic notification safety gate; does not authorize any action. */
object AgendaNudgePolicy {
    fun decide(
        candidate: AgendaNudge,
        now: Long,
        delivered: Set<String>,
        todayCount: Int,
        dailyLimit: Int,
        zone: ZoneId = ZoneId.systemDefault(),
        quietFrom: Int = 22,
        quietUntil: Int = 9
    ): AgendaNudgeDecision {
        if (candidate.key.isBlank() || dailyLimit !in 1..8)
            return AgendaNudgeDecision(false, "Invalid owner notification preference")
        if (candidate.key in delivered) return AgendaNudgeDecision(false, "Already notified")
        if (todayCount >= dailyLimit) return AgendaNudgeDecision(false, "Daily limit reached")
        val hour=Instant.ofEpochMilli(now).atZone(zone).hour
        val quiet=if(quietFrom > quietUntil)
            hour >= quietFrom || hour < quietUntil
        else hour >= quietFrom && hour < quietUntil
        if(quiet) return AgendaNudgeDecision(false, "Quiet hours")
        val delay=candidate.dueAt - now
        if(delay !in 0L..90L*60_000L)
            return AgendaNudgeDecision(false, "No imminent owner-approved event")
        return AgendaNudgeDecision(true, "Owner-approved event in next 90 minutes")
    }

    /** Feedback can only REDUCE discretionary nudges; never raise their daily cap. */
    fun limitFromFeedback(reactions: List<String>, base: Int = 3): Int {
        require(base in 1..8)
        val negative=reactions.takeLast(30).count { it=="DISMISSED" || it=="CORRECTED" }
        return if(negative>=3) (base-1).coerceAtLeast(1) else base
    }
}
