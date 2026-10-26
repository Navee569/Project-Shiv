package com.shiv.assistant.domain

enum class PowerMode { FULL, BALANCED, LIGHT }
enum class WorkClass { URGENT, ROUTER, CONVERSATION, SPEECH, BACKGROUND, CALIBRATION }
data class ResourceSnapshot(
    val batteryPercent: Int,
    val charging: Boolean,
    val thermalStatus: Int,
    val availableRamMb: Long,
    val screenOn: Boolean,
    val quietHours: Boolean
)
data class ComputeLease(
    val allowed: Boolean,
    val mode: PowerMode,
    val reason: String,
    val maxModelSeconds: Int,
    val allowLargeModel: Boolean
)
/**
 * Pure deterministic policy. Android service must supply real battery/thermal/RAM readings.
 * Thermal status uses Android PowerManager values: NONE=0, LIGHT=1, MODERATE=2, SEVERE=3.
 */
object ResourceGovernor {
    fun decide(
        state: ResourceSnapshot,
        requested: WorkClass,
        preference: PowerMode,
        usedSecondsToday: Int,
        dailyBudgetSeconds: Int
    ): ComputeLease {
        val hot = state.thermalStatus >= 3
        val warm = state.thermalStatus >= 2
        val lowBattery = state.batteryPercent <= 20 && !state.charging
        val capped = usedSecondsToday >= dailyBudgetSeconds.coerceAtLeast(0)
        val mode = when {
            hot || lowBattery || capped -> PowerMode.LIGHT
            preference == PowerMode.LIGHT -> PowerMode.LIGHT
            state.charging && preference == PowerMode.FULL -> PowerMode.FULL
            else -> PowerMode.BALANCED
        }
        if (requested == WorkClass.URGENT) {
            return ComputeLease(true, mode, "Urgent item bypasses discretionary power limits", 5, false)
        }
        if (hot) return ComputeLease(false, mode, "Device is too hot", 0, false)
        if (capped) return ComputeLease(false, mode, "Daily compute budget exhausted", 0, false)
        if (lowBattery && requested != WorkClass.ROUTER)
            return ComputeLease(false, mode, "Low battery", 0, false)
        if (state.quietHours && requested in setOf(WorkClass.BACKGROUND, WorkClass.CALIBRATION))
            return ComputeLease(false, mode, "Quiet hours", 0, false)
        if (requested in setOf(WorkClass.BACKGROUND, WorkClass.CALIBRATION) &&
            (!state.charging || state.screenOn))
            return ComputeLease(false, mode, "Heavy work requires charging and screen off", 0, false)
        val largeAllowed = !warm && !lowBattery && state.availableRamMb >= 2500L &&
            (state.charging || state.batteryPercent >= 60) && mode != PowerMode.LIGHT
        if (requested == WorkClass.CONVERSATION && !largeAllowed)
            return ComputeLease(true, mode, "Router fallback: insufficient resources for large model", 10, false)
        val seconds = when (mode) {
            PowerMode.FULL -> 60
            PowerMode.BALANCED -> 20
            PowerMode.LIGHT -> 5
        }
        return ComputeLease(true, mode, "Within resource budget", seconds,
            requested == WorkClass.CONVERSATION && largeAllowed)
    }
}
