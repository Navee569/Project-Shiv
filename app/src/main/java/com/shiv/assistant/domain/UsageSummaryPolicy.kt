package com.shiv.assistant.domain

/** Only anonymous aggregate app-duration metadata, never private screen contents. */
data class UsageObservation(val packageName:String, val foregroundMs:Long)
object UsageSummaryPolicy {
    fun filtered(data:List<UsageObservation>, excluded:Set<String> = emptySet()):
        List<UsageObservation> =
        data.filter { it.foregroundMs>=5*60_000L }
            .filter { it.packageName.isNotBlank() }
            .filterNot { SourceExclusions.excluded(it.packageName,excluded) }
            .sortedByDescending { it.foregroundMs }
            .take(10)
}
