package com.shiv.assistant.domain

/** States contain no phone number, contact or audio. */
enum class CallPhase { IDLE, RINGING, OFFHOOK }
object PostCallPolicy {
    fun shouldPrompt(previous: CallPhase, current: CallPhase, consent: Boolean,
        permission: Boolean, sensingAllowed: Boolean, localHour: Int): Boolean =
        previous == CallPhase.OFFHOOK && current == CallPhase.IDLE && consent &&
            permission && sensingAllowed && localHour in 9..21
}
