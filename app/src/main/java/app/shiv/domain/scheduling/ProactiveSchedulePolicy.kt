package app.shiv.domain.scheduling

/** Pure scheduling rules; the worker supplies current local time and stored settings. */
object ProactiveSchedulePolicy {
    fun isEligible(
        enabled: Boolean,
        maxDailyInterruptions: Int,
        minuteOfDay: Int,
        quietStartMinute: Int,
        quietEndMinute: Int
    ): Boolean = enabled && maxDailyInterruptions > 0 &&
        !isQuietMinute(minuteOfDay, quietStartMinute, quietEndMinute)

    fun isQuietMinute(minute: Int, startMinute: Int, endMinute: Int): Boolean {
        val safeMinute = minute.coerceIn(0, 1439)
        val start = startMinute.coerceIn(0, 1439)
        val end = endMinute.coerceIn(0, 1439)
        return when {
            start == end -> true
            start < end -> safeMinute in start until end
            else -> safeMinute >= start || safeMinute < end
        }
    }
}
