package app.shiv.data.scheduling

import androidx.room.withTransaction
import app.shiv.ActionLogEntry
import app.shiv.EventRecord
import app.shiv.ShivDatabase
import app.shiv.ShivSettings
import app.shiv.domain.scheduling.ProactiveAlert
import app.shiv.domain.scheduling.ProactiveAlertState
import app.shiv.domain.scheduling.ProactiveEvent
import app.shiv.domain.scheduling.ProactiveRepository
import app.shiv.domain.scheduling.ProactiveSettings
import app.shiv.domain.scheduling.ProactiveTransaction
import java.util.UUID

class RoomProactiveRepository(private val database: ShivDatabase) : ProactiveRepository {
    private val dao get() = database.dao()

    override suspend fun settings(): ProactiveSettings? = dao.settingsOnce()?.toDomain()

    override suspend fun countAlertsSince(since: Long): Int = dao.countProactiveSince(since)

    override suspend fun candidates(from: Long, until: Long): List<ProactiveEvent> =
        dao.proactiveEvents(from, until).map(EventRecord::toDomain)

    override suspend fun <T> withTransaction(block: suspend ProactiveTransaction.() -> T): T =
        database.withTransaction { RoomProactiveTransaction().block() }

    private inner class RoomProactiveTransaction : ProactiveTransaction {
        override suspend fun settings(): ProactiveSettings? = dao.settingsOnce()?.toDomain()

        override suspend fun event(eventId: String): ProactiveEvent? = dao.eventById(eventId)?.toDomain()

        override suspend fun alert(alertId: String): ProactiveAlert? = dao.actionById(alertId)?.toDomain()

        override suspend fun notificationTier(): String? = dao.actionTier("SEND_NOTIFICATION")

        override suspend fun countAlertsSince(since: Long): Int = dao.countProactiveSince(since)

        override suspend fun hasAlert(eventId: String): Boolean = dao.wasProactiveSent(eventId)

        override suspend fun deleteAlert(alertId: String) = dao.deleteAction(alertId)

        override suspend fun saveAlert(alert: ProactiveAlert) {
            dao.logAction(alert.toEntity())
        }
    }
}

private fun ShivSettings.toDomain() = ProactiveSettings(
    enabled = proactiveEnabled,
    quietStartMinute = quietStartMinute,
    quietEndMinute = quietEndMinute,
    maxDailyInterruptions = maxDailyInterruptions
)

private fun EventRecord.toDomain() = ProactiveEvent(
    id = id,
    title = title,
    startsAt = startsAt,
    place = place,
    person = person,
    source = source,
    createdAt = createdAt
)

private fun ProactiveAlert.toEntity() = ActionLogEntry(
    id = id,
    action = state.actionName,
    tier = tier,
    outcome = eventId,
    createdAt = createdAt
)

private fun ActionLogEntry.toDomain(): ProactiveAlert? {
    val state = ProactiveAlertState.entries.firstOrNull { it.actionName == action } ?: return null
    return ProactiveAlert(
        id = id,
        eventId = outcome,
        state = state,
        tier = tier,
        createdAt = createdAt
    )
}

internal fun newProactiveAlertId(): String = UUID.randomUUID().toString()
