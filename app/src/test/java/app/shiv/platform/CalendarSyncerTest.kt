package app.shiv.platform

import app.shiv.domain.integration.CalendarImportRepository
import app.shiv.domain.integration.CalendarSyncFailure
import app.shiv.domain.integration.ReminderSchedulingPort
import app.shiv.domain.model.ActionLogEntry
import app.shiv.domain.model.EventRecord
import app.shiv.domain.model.ReminderRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarSyncerTest {
    private val existing = EventRecord(id = "calendar-old", title = "Existing", startsAt = 10, source = "CALENDAR")
    private val incoming = EventRecord(id = "calendar-new", title = "Imported", startsAt = 20, source = "CALENDAR")

    @Test
    fun nullProviderCursorLeavesCalendarRowsAndAlarmsUntouched() = runBlocking {
        val repository = FakeCalendarRepository(mutableListOf(existing))
        val reminders = RecordingReminders()

        val result = CalendarSyncer.sync(CalendarEventSource { null }, repository, reminders)

        assertEquals(CalendarSyncFailure.PROVIDER_UNAVAILABLE, result.failureReason)
        assertFalse(result.eventsUpdated)
        assertEquals(listOf(existing), repository.events)
        assertTrue(reminders.cancelled.isEmpty())
        assertTrue(reminders.scheduled.isEmpty())
    }

    @Test
    fun providerFailureLeavesCalendarRowsAndAlarmsUntouched() = runBlocking {
        val repository = FakeCalendarRepository(mutableListOf(existing))
        val reminders = RecordingReminders()

        val result = CalendarSyncer.sync(CalendarEventSource { error("provider unavailable") }, repository, reminders)

        assertEquals(CalendarSyncFailure.PROVIDER_UNAVAILABLE, result.failureReason)
        assertEquals(listOf(existing), repository.events)
        assertTrue(reminders.cancelled.isEmpty())
    }

    @Test
    fun reminderFailureReportsEventsAlreadyReplaced() = runBlocking {
        val repository = FakeCalendarRepository(mutableListOf(existing))
        val reminders = RecordingReminders(failOnSchedule = true)

        val result = CalendarSyncer.sync(CalendarEventSource { listOf(incoming) }, repository, reminders)

        assertEquals(CalendarSyncFailure.REMINDER_SCHEDULING, result.failureReason)
        assertTrue(result.failed)
        assertTrue(result.eventsUpdated)
        assertFalse(result.remindersUpdated)
        assertEquals(listOf(incoming), repository.events)
        assertEquals(listOf(existing), reminders.cancelled)
    }

    @Test
    fun persistenceFailureLeavesExistingRowsAndAlarmsUntouched() = runBlocking {
        val repository = FakeCalendarRepository(mutableListOf(existing), failReplacement = true)
        val reminders = RecordingReminders()

        val result = CalendarSyncer.sync(CalendarEventSource { listOf(incoming) }, repository, reminders)

        assertEquals(CalendarSyncFailure.EVENT_PERSISTENCE, result.failureReason)
        assertFalse(result.eventsUpdated)
        assertEquals(listOf(existing), repository.events)
        assertTrue(reminders.cancelled.isEmpty())
    }

    private class FakeCalendarRepository(
        val events: MutableList<EventRecord>,
        private val failReplacement: Boolean = false
    ) : CalendarImportRepository {
        override suspend fun calendarEvents() = events.toList()
        override suspend fun replaceCalendarEvents(events: List<EventRecord>) {
            if (failReplacement) error("database unavailable")
            this.events.clear()
            this.events.addAll(events)
        }
        override suspend fun reminderRule(key: String) = ReminderRule(key, "10")
        override suspend fun recordCalendarImport(eventCount: Int) = Unit
    }

    private class RecordingReminders(private val failOnSchedule: Boolean = false) : ReminderSchedulingPort {
        val cancelled = mutableListOf<EventRecord>()
        val scheduled = mutableListOf<EventRecord>()
        override fun cancel(event: EventRecord) { cancelled += event }
        override fun schedule(event: EventRecord, leadMinutes: Int) {
            if (failOnSchedule) error("alarm manager unavailable")
            scheduled += event
        }
    }
}
