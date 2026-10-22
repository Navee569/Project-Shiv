package app.shiv

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationEventOrchestratorTest {
    private val event = EventRecord(id = "event-1", title = "Project review", startsAt = 2_000_000_000_000L, source = "NOTIFICATION")

    private class FakeSink(var duplicate: Boolean = false) : NotificationEventSink {
        val calls = mutableListOf<String>()
        override suspend fun isDuplicate(event: EventRecord): Boolean {
            calls += "check"
            return duplicate
        }
        override suspend fun createPendingConfirmation(event: EventRecord) { calls += "pending:${event.source}" }
        override suspend fun saveEvent(event: EventRecord) { calls += "save:${event.source}" }
        override suspend fun scheduleReminder(event: EventRecord) { calls += "schedule" }
        override suspend fun notifySavedEvent(event: EventRecord) { calls += "notify" }
    }

    @Test fun freeTierSavesAndSchedulesWithoutNotification() = runBlocking {
        val sink = FakeSink()
        assertEquals(NotificationEventOutcome.SAVED, orchestrateNotificationEvent(event, ActionTier.FREE, sink))
        assertEquals(listOf("check", "save:NOTIFICATION", "schedule"), sink.calls)
    }

    @Test fun actThenTellSavesSchedulesAndNotifies() = runBlocking {
        val sink = FakeSink()
        assertEquals(NotificationEventOutcome.SAVED_AND_NOTIFIED, orchestrateNotificationEvent(event, ActionTier.ACT_THEN_TELL, sink))
        assertEquals(listOf("check", "save:NOTIFICATION", "schedule", "notify"), sink.calls)
    }

    @Test fun askFirstOnlyCreatesPendingConfirmation() = runBlocking {
        val sink = FakeSink()
        assertEquals(NotificationEventOutcome.PENDING_CONFIRMATION, orchestrateNotificationEvent(event, ActionTier.ASK_FIRST, sink))
        assertEquals(listOf("check", "pending:NOTIFICATION"), sink.calls)
    }

    @Test fun duplicateNotificationHasNoSideEffects() = runBlocking {
        val sink = FakeSink(duplicate = true)
        assertEquals(NotificationEventOutcome.DUPLICATE, orchestrateNotificationEvent(event, ActionTier.ACT_THEN_TELL, sink))
        assertEquals(listOf("check"), sink.calls)
    }
}
