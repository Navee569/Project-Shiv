package app.shiv.data.repository

import android.content.Context
import androidx.room.Room
import app.shiv.data.local.ShivDatabase
import app.shiv.domain.integration.CaptureQueueProcessor
import app.shiv.domain.integration.CaptureRunResult
import app.shiv.domain.integration.CaptureSignalIntake
import app.shiv.domain.model.CapturePriority
import app.shiv.domain.model.CaptureSignal
import app.shiv.domain.model.CaptureStatus
import app.shiv.domain.model.EventRecord
import app.shiv.domain.model.RecordSourceMetadata
import app.shiv.domain.model.ReminderRecord
import app.shiv.domain.repository.CaptureOutcome
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class CaptureQueuePersistenceTest {
    private lateinit var database: ShivDatabase
    private lateinit var queue: RoomCaptureQueueRepository
    private lateinit var repository: RoomShivViewRepository

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, ShivDatabase::class.java).build()
        queue = RoomCaptureQueueRepository(database)
        repository = RoomShivViewRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun capturesDeduplicateAndClaimInPriorityOrderWithDurableRetryState() = runBlocking {
        val normal = CaptureSignal(id = "normal", payload = "meeting with Raj", source = "LISTENING", capturedAt = 10, priority = CapturePriority.NORMAL)
        val urgent = CaptureSignal(id = "urgent", payload = "urgent call", source = "NOTIFICATION", capturedAt = 11, priority = CapturePriority.URGENT)
        assertTrue(queue.enqueue(normal))
        assertFalse(queue.enqueue(normal.copy(payload = "duplicate payload")))
        assertTrue(queue.enqueue(urgent))
        assertEquals("meeting with Raj", queue.get("normal")?.payload)

        val first = queue.claimNext(now = 20, leaseMillis = 100)!!
        assertEquals("urgent", first.signal.id)
        assertEquals(CaptureStatus.PROCESSING, first.signal.status)
        assertEquals(1, first.signal.attempts)
        assertTrue(queue.retry(first, nextAttemptAt = 200, failureCode = "NOTIFICATION_READ_FAILED"))
        assertEquals(CaptureStatus.RETRY, queue.get("urgent")?.status)
        assertEquals("urgent call", queue.get("urgent")?.payload)
        val normalLease = queue.claimNext(now = 199, leaseMillis = 100)!!
        assertEquals("normal", normalLease.signal.id)
        assertTrue(queue.complete(normalLease, CaptureOutcome(), completedAt = 199))

        val retry = queue.claimNext(now = 200, leaseMillis = 100)!!
        assertEquals("urgent", retry.signal.id)
        assertEquals(2, retry.signal.attempts)
        val event = EventRecord(id = "event-urgent", title = "Call", startsAt = 500, source = "NOTIFICATION", confidence = 0.82)
        val completed = queue.complete(
            retry,
            CaptureOutcome(
                event = event,
                reminder = ReminderRecord(id = "reminder-urgent", eventId = event.id, triggerAt = 400),
                sourceMetadata = RecordSourceMetadata(recordType = "EVENT", recordId = event.id, source = "NOTIFICATION", confidence = 0.82, capturedAt = 11)
            ),
            completedAt = 250
        )
        assertTrue(completed)
        assertEquals(CaptureStatus.COMPLETED, queue.get("urgent")?.status)
        assertEquals("", queue.get("urgent")?.payload)
        assertEquals(listOf(event), repository.events(0).first())
        val persistedReminder = repository.remindersForEvent(event.id).single()
        assertEquals("reminder-urgent", persistedReminder.id)
        assertEquals(400L, persistedReminder.triggerAt)
        assertEquals(0.82, repository.recordSources("EVENT", event.id).single().confidence!!, 0.0001)
        assertNull(queue.claim("urgent", now = 300, leaseMillis = 100))
    }

    @Test
    fun concurrentWorkersCanClaimOneCaptureOnlyOnce() = runBlocking {
        queue.enqueue(CaptureSignal(id = "one", payload = "important", source = "LISTENING", capturedAt = 10))
        val attempts = (0 until 12).map { async(Dispatchers.IO) { queue.claim("one", now = 100, leaseMillis = 1_000) } }.map { it.await() }
        assertEquals(1, attempts.count { it != null })
    }

    @Test
    fun processorConsumesHighestPrioritySignalFirst() = runBlocking {
        queue.enqueue(CaptureSignal(id = "background", payload = "later", source = "LISTENING", capturedAt = 1, priority = CapturePriority.BACKGROUND))
        queue.enqueue(CaptureSignal(id = "urgent", payload = "now", source = "NOTIFICATION", capturedAt = 2, priority = CapturePriority.URGENT))
        val order = mutableListOf<String>()
        val processor = CaptureQueueProcessor(queue, nowMillis = { 10 }) { signal ->
            order += signal.id
            CaptureOutcome()
        }

        assertEquals(CaptureRunResult.COMPLETED, processor.processNext().result)
        assertEquals(CaptureRunResult.COMPLETED, processor.processNext().result)
        assertEquals(listOf("urgent", "background"), order)
    }

    @Test
    fun incomingSignalIsCommittedBeforeProcessingIsScheduled() = runBlocking {
        val signal = CaptureSignal(id = "save-first", payload = "meet Raj", source = "NOTIFICATION", capturedAt = 10)
        var scheduled = 0
        val intake = CaptureSignalIntake(queue) { saved ->
            assertEquals(signal, queue.get(saved.id))
            scheduled++
        }

        assertTrue(intake.receive(signal))
        assertFalse(intake.receive(signal))
        assertEquals(1, scheduled)
    }

    @Test
    fun queueProcessorRetriesWithoutLosingInputAndDoesNotRepeatCompletedEffects() = runBlocking {
        val signal = CaptureSignal(id = "retry", payload = "meet Raj tomorrow", source = "LISTENING", capturedAt = 10)
        queue.enqueue(signal)
        var now = 100L
        val calls = AtomicInteger()
        val processor = CaptureQueueProcessor(queue, nowMillis = { now }, leaseMillis = 10_000) { input ->
            assertEquals(signal.payload, input.payload)
            if (calls.incrementAndGet() == 1) error("simulated inference failure")
            val event = EventRecord(id = "event-${input.id}", title = "Meeting", startsAt = 1_000, source = input.source)
            CaptureOutcome(event = event, sourceMetadata = RecordSourceMetadata(recordType = "EVENT", recordId = event.id, source = input.source))
        }

        assertEquals(CaptureRunResult.RETRY_SCHEDULED, processor.process(signal.id))
        assertEquals(CaptureStatus.RETRY, queue.get(signal.id)?.status)
        assertEquals(signal.payload, queue.get(signal.id)?.payload)
        now += 1_000
        assertEquals(CaptureRunResult.COMPLETED, processor.process(signal.id))
        assertEquals(CaptureRunResult.ALREADY_COMPLETED, processor.process(signal.id))
        assertEquals(2, calls.get())
        assertEquals(listOf("event-${signal.id}"), repository.events(0).first().map { it.id })
    }

    @Test
    fun expiredProcessingLeaseCanBeRecoveredAfterRestart() = runBlocking {
        val signal = CaptureSignal(id = "crash", payload = "keep this", source = "NOTIFICATION", capturedAt = 10)
        queue.enqueue(signal)
        val first = queue.claim(signal.id, now = 100, leaseMillis = 50)!!
        assertNotNull(first.signal.leaseToken)
        assertNull(queue.claim(signal.id, now = 149, leaseMillis = 50))
        val recovered = queue.claim(signal.id, now = 150, leaseMillis = 50)!!
        assertEquals(signal.id, recovered.signal.id)
        assertEquals("keep this", recovered.signal.payload)
        assertEquals(2, recovered.signal.attempts)
        assertFalse(queue.complete(first, CaptureOutcome(), completedAt = 151))
        assertTrue(queue.complete(recovered, CaptureOutcome(), completedAt = 152))
    }

    @Test
    fun eventReminderAndCompletionRollBackTogetherOnConstraintFailure() = runBlocking {
        val signal = CaptureSignal(id = "rollback", payload = "meeting", source = "LISTENING", capturedAt = 10)
        queue.enqueue(signal)
        val lease = queue.claim(signal.id, now = 10, leaseMillis = 100)!!
        val event = EventRecord(id = "rollback-event", title = "Meeting", startsAt = 900, source = "LISTENING")
        repository.saveEvent(event.copy(id = "pre-existing-event"))
        repository.saveReminder(ReminderRecord(id = "existing-reminder", eventId = "pre-existing-event", triggerAt = 800))
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_capture_reminder BEFORE INSERT ON reminders WHEN NEW.id = 'duplicate-reminder' BEGIN SELECT RAISE(ABORT, 'simulated reminder persistence failure'); END"
        )

        runCatching {
            queue.complete(
                lease,
                CaptureOutcome(
                    event = event,
                    reminder = ReminderRecord(id = "duplicate-reminder", eventId = event.id, triggerAt = 800)
                ),
                completedAt = 20
            )
        }.onSuccess { error("Unique reminder conflict should abort the transaction") }

        assertEquals(listOf("pre-existing-event"), repository.events(0).first().map { it.id })
        assertEquals(CaptureStatus.PROCESSING, queue.get(signal.id)?.status)
        assertEquals("existing-reminder", repository.remindersForEvent("pre-existing-event").single().id)
    }
}
