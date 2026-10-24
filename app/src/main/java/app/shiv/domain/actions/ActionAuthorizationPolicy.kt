package app.shiv.domain.actions

/** Safety level required before Shiv carries out an action. */
enum class ActionTier { FREE, ACT_THEN_TELL, ASK_FIRST }

/** Pure rules for resolving configured action tiers and background authorization. */
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
}
