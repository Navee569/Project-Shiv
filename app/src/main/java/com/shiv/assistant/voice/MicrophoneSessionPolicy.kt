package com.shiv.assistant.voice

/** Deterministic bounds for user-started offline microphone service. */
object MicrophoneSessionPolicy {
    const val MAX_SESSION_MS = 10 * 60 * 1000L
    const val LOW_BATTERY_PCT = 25
    const val MAX_ERRORS = 5
    fun retryDelayMs(silentRounds:Int):Long=when {
        silentRounds<2 -> 1_500L
        silentRounds<5 -> 5_000L
        else -> 15_000L
    }
    fun pocketed(proximityCm:Float?, proximityMaxCm:Float?, lux:Float?):Boolean =
        proximityCm != null && proximityMaxCm != null && lux != null &&
            proximityCm < proximityMaxCm && lux < 8f
    fun sleepHours(hour:Int):Boolean=hour>=22 || hour<9
    fun wakeOnly(value:String):Boolean=Regex("^shiv[,.!?]?$",RegexOption.IGNORE_CASE)
        .matches(value.trim())
    fun wakeCommand(value:String,required:Boolean):String? {
        val cleaned=value.trim()
        if(!required)return safeUtterance(cleaned)
        val match=Regex("(?i)^shiv[,.!?\\s:;-]+(.+)$").matchEntire(cleaned) ?: return null
        return safeUtterance(match.groupValues[1])
    }
    fun mayContinue(startedAt: Long, now: Long, batteryPct: Int,
        thermalStatus: Int, recognizerAvailable: Boolean, errors: Int,
        continuous:Boolean=false): Boolean =
        recognizerAvailable && now >= startedAt && (continuous || now - startedAt < MAX_SESSION_MS) &&
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
