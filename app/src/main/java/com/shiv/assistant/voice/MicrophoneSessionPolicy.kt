package com.shiv.assistant.voice

/** Deterministic bounds for user-started offline microphone service. */
object MicrophoneSessionPolicy {
    const val MAX_SESSION_MS = 10 * 60 * 1000L
    const val LOW_BATTERY_PCT = 25
    const val MAX_ERRORS = 5
    fun mayContinue(startedAt: Long, now: Long, batteryPct: Int,
        thermalStatus: Int, recognizerAvailable: Boolean, errors: Int): Boolean =
        recognizerAvailable && now >= startedAt && now - startedAt < MAX_SESSION_MS &&
        batteryPct >= LOW_BATTERY_PCT && thermalStatus < 4 && errors < MAX_ERRORS
    fun safeUtterance(value: String): String? {
        val text=value.trim()
        if (text.length !in 5..500) return null
        if(Regex("(?i)\\b(otp|password|pin|cvv|recovery code|verification code)\\b")
                .containsMatchIn(text))return null
        if(Regex("\\b[0-9]{5,}\\b").containsMatchIn(text))return null
        return text
    }
}
