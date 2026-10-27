package com.shiv.assistant.data

/** Only bland low-priority background categories may be merged. */
object SignalDedupePolicy {
    private val bland=setOf("weather","traffic","promotion")
    fun mayMerge(signal:SignalRecord):Boolean =
        signal.priority<=2 && signal.source.startsWith("notification:") &&
            signal.fact.trim().lowercase(java.util.Locale.ROOT) in bland
}
