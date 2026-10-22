package app.shiv

/** One fail-safe policy for user-configured action tiers across all input sources. */
internal object ActionTierPolicy {
    fun resolve(stored: String?, defaultTier: ActionTier = ActionTier.FREE, minimum: ActionTier = ActionTier.FREE): ActionTier {
        val requested = if (stored == null) defaultTier else runCatching { ActionTier.valueOf(stored) }.getOrElse { ActionTier.ASK_FIRST }
        return if (requested.ordinal < minimum.ordinal) minimum else requested
    }
}
