package com.shiv.assistant.data

/** Age limits are source-specific; stale input is recorded but never reinterpreted. */
object SignalFreshnessPolicy {
    fun isFresh(source:String,receivedAt:Long,now:Long):Boolean {
        if(receivedAt<=0 || receivedAt>now+60_000L)return false
        val limit=when(source.substringBefore(':')) {
            "notification","screen" -> 6*60*60_000L
            "long_idle_unlock","task_deadline","call-end" -> 2*60*60_000L
            "calendar-change" -> 48*60*60_000L
            else -> 24*60*60_000L
        }
        return now-receivedAt<=limit
    }
}
