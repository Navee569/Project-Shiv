package com.shiv.assistant.domain

/** Owner-feedback-only adjustment of *optional* interruption count.
 * True urgent alarms and owner-requested reminders bypass this policy. */
enum class ReviewReaction { ACTED, SNOOZED, DISMISSED, CORRECTED }
object SelfReviewPolicy {
    fun suggestedDailyCap(reactions: List<ReviewReaction>, base: Int = 3): Int {
        require(base in 1..10)
        val negative = reactions.count {
            it == ReviewReaction.DISMISSED || it == ReviewReaction.CORRECTED
        }
        return when {
            negative >= 3 -> (base - 1).coerceAtLeast(1)
            else -> base
        }
    }
}
