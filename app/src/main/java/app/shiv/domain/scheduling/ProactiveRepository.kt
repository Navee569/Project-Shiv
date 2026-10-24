package app.shiv.domain.scheduling

data class ProactiveSettings(
    val enabled: Boolean,
    val quietStartMinute: Int,
    val quietEndMinute: Int,
    val maxDailyInterruptions: Int
) {
    companion object {
        val DEFAULT = ProactiveSettings(
            enabled = true,
            quietStartMinute = 22 * 60,
            quietEndMinute = 8 * 60,
            maxDailyInterruptions = 3
        )
    }
}

data class ProactiveEvent(
    val id: String,
    val title: String,
    val startsAt: Long,
    val place: String?,
    val person: String?,
    val source: String,
    val createdAt: Long
)

enum class ProactiveAlertState(val actionName: String) {
    RESERVED("Proactive notification reserved"),
    SENT("Proactive notification")
}

data class ProactiveAlert(
    val id: String,
    val eventId: String,
    val state: ProactiveAlertState,
    val tier: String,
    val createdAt: Long
)

/** Storage contract for proactive scheduling; Android and Room types stay behind this port. */
interface ProactiveRepository {
    suspend fun settings(): ProactiveSettings?
    suspend fun countAlertsSince(since: Long): Int
    suspend fun candidates(from: Long, until: Long): List<ProactiveEvent>
    suspend fun <T> withTransaction(block: suspend ProactiveTransaction.() -> T): T
}

/** Operations available inside one atomic reservation/final-dispatch transaction. */
interface ProactiveTransaction {
    suspend fun settings(): ProactiveSettings?
    suspend fun event(eventId: String): ProactiveEvent?
    suspend fun alert(alertId: String): ProactiveAlert?
    suspend fun notificationTier(): String?
    suspend fun countAlertsSince(since: Long): Int
    suspend fun hasAlert(eventId: String): Boolean
    suspend fun deleteAlert(alertId: String)
    suspend fun saveAlert(alert: ProactiveAlert)
}
