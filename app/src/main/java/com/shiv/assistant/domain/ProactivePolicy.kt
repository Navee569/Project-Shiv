package com.shiv.assistant.domain

import java.time.*
import com.shiv.assistant.ai.InferencePriority

enum class ProactiveOutcome { ACT, ASK, NOTIFY, STAY_QUIET }
data class ProactiveInputs(
    val urgency: Int, val relevance: Double, val confidence: Double,
    val minutesToEvent: Long?, val now: ZonedDateTime,
    val quietFrom: LocalTime = LocalTime.of(22,0), val quietUntil: LocalTime = LocalTime.of(9,0),
    val interruptionsToday: Int, val dailyLimit: Int = 4,
    val alreadyNotified: Boolean = false, val userBusy: Boolean = false,
    val batteryLow: Boolean = false
)
data class ProactiveDecision(val outcome: ProactiveOutcome, val reason: String)
object ProactivePolicy {
    fun decide(x: ProactiveInputs): ProactiveDecision {
        val quiet = if(x.quietFrom < x.quietUntil)
            x.now.toLocalTime() in x.quietFrom..x.quietUntil
        else x.now.toLocalTime() >= x.quietFrom || x.now.toLocalTime() < x.quietUntil
        if (x.alreadyNotified) return ProactiveDecision(ProactiveOutcome.STAY_QUIET,"Duplicate suppressed")
        if (x.urgency < 9 && quiet) return ProactiveDecision(ProactiveOutcome.STAY_QUIET,"Quiet hours")
        if (x.urgency < 9 && x.interruptionsToday >= x.dailyLimit)
            return ProactiveDecision(ProactiveOutcome.STAY_QUIET,"Daily interruption limit")
        if (x.confidence < 0.65 || x.relevance < 0.50)
            return ProactiveDecision(ProactiveOutcome.STAY_QUIET,"Weak evidence")
        if (x.urgency >= 9 && x.confidence > 0.85)
            return ProactiveDecision(ProactiveOutcome.NOTIFY,"Urgent event")
        if (x.userBusy || x.batteryLow)
            return ProactiveDecision(ProactiveOutcome.STAY_QUIET,"Deferring nonurgent interruption")
        if (x.minutesToEvent != null && x.minutesToEvent in 0..30)
            return ProactiveDecision(ProactiveOutcome.NOTIFY,"Upcoming event")
        return ProactiveDecision(ProactiveOutcome.STAY_QUIET,"No justified interruption")
    }
}
data class PowerContext(val batteryPct: Int, val charging: Boolean, val hot: Boolean, val budgetUsed: Int)
enum class PowerMode { FULL, BALANCED, LIGHT }
object PowerPolicy {
    fun mode(context: PowerContext): PowerMode =
        when { context.hot || context.batteryPct < 20 || context.budgetUsed >= 100 -> PowerMode.LIGHT
            context.charging -> PowerMode.FULL
            else -> PowerMode.BALANCED }
    fun allow(priority: InferencePriority, mode: PowerMode): Boolean =
        priority == InferencePriority.URGENT || mode != PowerMode.LIGHT
}
