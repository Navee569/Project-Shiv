package com.shiv.assistant.domain

object ProactiveTriggerPolicy {
    const val LONG_IDLE_MS=2*60*60_000L
    const val DEADLINE_WINDOW_MS=60*60_000L
    fun longIdleUnlock(shownAt:Long?,hiddenAt:Long,lastRecordedAt:Long):Boolean =
        shownAt!=null && hiddenAt>lastRecordedAt && hiddenAt>=shownAt &&
            hiddenAt-shownAt>=LONG_IDLE_MS
    fun approachingDeadline(dueAt:Long?,now:Long):Boolean =
        dueAt!=null && dueAt in now..now+DEADLINE_WINDOW_MS
}
