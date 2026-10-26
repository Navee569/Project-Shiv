package com.shiv.assistant.domain

data class AppUsageObservation(val packageName: String, val durationMs: Long)

/** Denied source identifiers must never enter display, logs, DB or model context. */
object UsageSummaryPolicy {
    fun allowed(items: List<AppUsageObservation>,
        excluded: Set<String> = emptySet()): List<AppUsageObservation> =
        items.filter { it.durationMs >= 5 * 60_000L && it.durationMs >= 0L }
            .filterNot { PrivacyPolicy.isDeniedPackage(it.packageName, excluded) }
            .sortedByDescending { it.durationMs }.take(10)
}
