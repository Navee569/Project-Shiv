package app.shiv.domain.integration

import app.shiv.domain.actions.ActionPermissionState
import app.shiv.domain.model.EventRecord
import app.shiv.domain.model.ReminderRule

/** Platform permission state supplied to deterministic action authorization. */
fun interface ActionPermissionPort {
    fun stateFor(actionType: String): ActionPermissionState
}

/** Android alarm operations behind a platform-free scheduling boundary. */
interface ReminderSchedulingPort {
    fun schedule(event: EventRecord, leadMinutes: Int)
    fun cancel(event: EventRecord)
}

data class CalendarSyncResult(
    val importedCount: Int = 0,
    val permissionDenied: Boolean = false,
    val failed: Boolean = false,
    val eventsUpdated: Boolean = false,
    val remindersUpdated: Boolean = false,
    val failureReason: CalendarSyncFailure? = null
)

enum class CalendarSyncFailure { PROVIDER_UNAVAILABLE, EVENT_PERSISTENCE, REMINDER_SCHEDULING, AUDIT_WRITE }

/** Atomic calendar-owned event replacement and its local reminder preference/audit record. */
interface CalendarImportRepository {
    suspend fun calendarEvents(): List<EventRecord>
    suspend fun replaceCalendarEvents(events: List<EventRecord>)
    suspend fun reminderRule(key: String): ReminderRule?
    suspend fun recordCalendarImport(eventCount: Int)
}

/** Reads and imports events from the device calendar when the owner grants access. */
fun interface CalendarSyncPort {
    suspend fun synchronize(): CalendarSyncResult
}

enum class DiagnosticArea { ACTION, CALENDAR }

enum class DiagnosticCode { AUDIT_WRITE_FAILED, ACTION_DISPATCH_FAILED, CALENDAR_PERMISSION_DENIED, CALENDAR_SYNC_FAILED }

/** Deliberately contains no user text, action arguments, or exception messages. */
data class DiagnosticEvent(
    val area: DiagnosticArea,
    val code: DiagnosticCode,
    val occurredAtMillis: Long
)

fun interface DiagnosticsPort {
    suspend fun record(event: DiagnosticEvent)
}

/** Isolates Android notification permission checks and notification delivery. */
interface NotificationDeliveryPort {
    fun allowed(): Boolean
    /** False means definitely not delivered; a thrown failure has an uncertain delivery outcome. */
    fun post(eventId: String, title: String, body: String): Boolean
}
