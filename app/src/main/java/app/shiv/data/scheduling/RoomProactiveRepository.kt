package app.shiv.data.scheduling

import androidx.room.withTransaction
import app.shiv.ShivDatabase
import app.shiv.data.mapping.RoomRecordMappers
import app.shiv.domain.scheduling.ProactiveAlert
import app.shiv.domain.scheduling.ProactiveAlertState
import app.shiv.domain.scheduling.ProactiveEvent
import app.shiv.domain.scheduling.ProactiveRepository
import app.shiv.domain.scheduling.ProactiveSettings
import app.shiv.domain.scheduling.ProactiveTransaction
import java.util.UUID

class RoomProactiveRepository(private val database: ShivDatabase) : ProactiveRepository {
    private val dao get() = database.dao()

    override suspend fun settings(): ProactiveSettings? = dao.settingsOnce()?.let(RoomRecordMappers::toDomain)?.toProactiveSettings()

    override suspend fun countAlertsSince(since: Long): Int = dao.countProactiveSince(since)

    override suspend fun candidates(from: Long, until: Long): List<ProactiveEvent> =
        dao.proactiveEvents(from, until).map { RoomRecordMappers.toDomain(it).toProactiveEvent() }

    override suspend fun <T> withTransaction(block: suspend ProactiveTransaction.() -> T): T =
        database.withTransaction { RoomProactiveTransaction().block() }

    private inner class RoomProactiveTransaction : ProactiveTransaction {
        override suspend fun settings(): ProactiveSettings? = dao.settingsOnce()?.let(RoomRecordMappers::toDomain)?.toProactiveSettings()
        override suspend fun event(eventId: String): ProactiveEvent? = dao.eventById(eventId)?.let(RoomRecordMappers::toDomain)?.toProactiveEvent()
        override suspend fun alert(alertId: String): ProactiveAlert? = dao.actionById(alertId)?.let(RoomRecordMappers::toDomain)?.toProactiveAlert()
        override suspend fun notificationTier(): String? = dao.actionTier("SEND_NOTIFICATION")
        override suspend fun countAlertsSince(since: Long): Int = dao.countProactiveSince(since)
        override suspend fun hasAlert(eventId: String): Boolean = dao.wasProactiveSent(eventId)
        override suspend fun deleteAlert(alertId: String) = dao.deleteAction(alertId)
        override suspend fun saveAlert(alert: ProactiveAlert) {
            val record = app.shiv.ActionLogEntry(alert.id, alert.state.actionName, alert.tier, alert.eventId, alert.createdAt)
            dao.logAction(RoomRecordMappers.toRoom(record))
        }
    }
}

private fun app.shiv.ShivSettings.toProactiveSettings() = ProactiveSettings(
    enabled = proactiveEnabled,
    quietStartMinute = quietStartMinute,
    quietEndMinute = quietEndMinute,
    maxDailyInterruptions = maxDailyInterruptions
)

private fun app.shiv.EventRecord.toProactiveEvent() = ProactiveEvent(
    id = id, title = title, startsAt = startsAt, place = place,
    person = person, source = source, createdAt = createdAt
)

private fun app.shiv.ActionLogEntry.toProactiveAlert(): ProactiveAlert? {
    val state = ProactiveAlertState.entries.firstOrNull { it.actionName == action } ?: return null
    return ProactiveAlert(id = id, eventId = outcome, state = state, tier = tier, createdAt = createdAt)
}

internal fun newProactiveAlertId(): String = UUID.randomUUID().toString()
