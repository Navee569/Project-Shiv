package app.shiv.domain.actions

/** Safety level required before Shiv carries out an action. */
enum class ActionTier { FREE, ACT_THEN_TELL, ASK_FIRST }

enum class ActionPermissionState { NOT_REQUIRED, GRANTED, DENIED }

enum class ActionAuthorizationOutcome { ALLOW, REQUIRE_CONFIRMATION, DENY_PERMISSION, DENY_INVALID_REQUEST }

/** A model or UI proposal, including the inputs that constrain its authority. */
data class ActionAuthorizationRequest(
    val actionType: String,
    val arguments: Map<String, String>,
    val configuredTier: String?,
    val minimumTier: ActionTier,
    val permissionState: ActionPermissionState = ActionPermissionState.NOT_REQUIRED,
    val confirmed: Boolean = false
)

data class ActionAuthorizationDecision(
    val request: ActionAuthorizationRequest,
    val effectiveTier: ActionTier,
    val outcome: ActionAuthorizationOutcome
)

/** Pure rules for resolving configured action tiers and authorization decisions. */
object ActionAuthorizationPolicy {
    /** A stored tier may make an action safer, but can never lower its required minimum. */
    fun effectiveTier(configuredTier: String?, minimum: ActionTier): ActionTier {
        val configured = configuredTier
            ?.let { runCatching { ActionTier.valueOf(it) }.getOrNull() }
            ?: minimum
        return if (configured.ordinal < minimum.ordinal) minimum else configured
    }

    /** Background actions that require confirmation cannot be dispatched automatically. */
    fun backgroundTier(configuredTier: String?, minimum: ActionTier): ActionTier? =
        effectiveTier(configuredTier, minimum).takeUnless { it == ActionTier.ASK_FIRST }

    fun authorize(request: ActionAuthorizationRequest): ActionAuthorizationDecision {
        val tier = effectiveTier(request.configuredTier, request.minimumTier)
        val outcome = when {
            request.actionType.isBlank() || request.arguments.keys.any(String::isBlank) ->
                ActionAuthorizationOutcome.DENY_INVALID_REQUEST
            request.permissionState == ActionPermissionState.DENIED ->
                ActionAuthorizationOutcome.DENY_PERMISSION
            tier == ActionTier.ASK_FIRST && !request.confirmed ->
                ActionAuthorizationOutcome.REQUIRE_CONFIRMATION
            else -> ActionAuthorizationOutcome.ALLOW
        }
        return ActionAuthorizationDecision(request, tier, outcome)
    }
}
