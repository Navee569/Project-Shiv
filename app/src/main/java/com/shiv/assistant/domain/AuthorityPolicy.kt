package com.shiv.assistant.domain

enum class AuthorityTier { FREE, ACT_THEN_TELL, ASK_FIRST }
enum class ActionAuthority { NOT_ALLOWED, NEEDS_CONFIRMATION, EXECUTE }
data class Authorization(
    val decision: ActionAuthority,
    val tier: AuthorityTier,
    val reason: String
)
/** Model-generated JSON can propose arguments but can never raise its own authority. */
object AuthorityPolicy {
    private val hardFloor = setOf("PAYMENT", "PERMANENT_DELETE", "SEND_MESSAGE", "SOCIAL_PUBLISH", "PRIVILEGED")
    fun authorize(
        actionType: String,
        configuredTier: AuthorityTier,
        hasPermission: Boolean,
        explicitConfirmation: Boolean,
        fromDeniedSource: Boolean = false
    ): Authorization {
        val type = actionType.trim().uppercase()
        if (type.isEmpty() || fromDeniedSource)
            return Authorization(ActionAuthority.NOT_ALLOWED, AuthorityTier.ASK_FIRST, "Untrusted or missing action")
        val tier = if (type in hardFloor) AuthorityTier.ASK_FIRST else configuredTier
        if (!hasPermission)
            return Authorization(ActionAuthority.NOT_ALLOWED, tier, "Required permission missing")
        if (tier == AuthorityTier.ASK_FIRST && !explicitConfirmation)
            return Authorization(ActionAuthority.NEEDS_CONFIRMATION, tier, "Owner confirmation required")
        return Authorization(ActionAuthority.EXECUTE, tier, "Authorized by deterministic policy")
    }
    fun maySuggestPromotion(actionType: String, approvals: Int, threshold: Int = 5): Boolean =
        approvals >= threshold && actionType.trim().uppercase() !in hardFloor
}
