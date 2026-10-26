package com.shiv.assistant.domain

data class NudgeCandidate(
    val id: String, val importance: Int, val minutesUntil: Int, val confidence: Double
)
data class InterruptionContext(
    val quietHours: Boolean,
    val todayCount: Int,
    val dailyCap: Int,
    val recentlyNotifiedIds: Set<String>,
    val sensingEnabled: Boolean
)
data class NudgeDecision(val shouldNotify: Boolean, val reason: String)
/** Actual pattern extraction needs owner-consented signals; never generate imaginary predictions. */
object Anticipation {
    fun decide(nudge: NudgeCandidate, context: InterruptionContext): NudgeDecision = when {
        !context.sensingEnabled -> NudgeDecision(false, "Sensing paused")
        nudge.id in context.recentlyNotifiedIds -> NudgeDecision(false, "Duplicate suppressed")
        context.quietHours && nudge.importance < 90 -> NudgeDecision(false, "Quiet hours")
        context.todayCount >= context.dailyCap && nudge.importance < 90 ->
            NudgeDecision(false, "Interruption cap")
        nudge.confidence < 0.75 -> NudgeDecision(false, "Insufficient evidence")
        nudge.minutesUntil < -5 -> NudgeDecision(false, "Stale")
        nudge.importance < 40 -> NudgeDecision(false, "Not worth interruption")
        else -> NudgeDecision(true, "High-confidence time-relevant item")
    }
}
