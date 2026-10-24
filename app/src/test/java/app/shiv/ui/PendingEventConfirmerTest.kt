package app.shiv.ui

import android.content.Context
import androidx.room.Room
import app.shiv.data.local.ShivDatabase
import app.shiv.data.repository.RoomShivViewRepository
import app.shiv.domain.actions.ActionAuditRepository
import app.shiv.domain.actions.ActionAuthorizationRequest
import app.shiv.domain.actions.ActionExecutionStatus
import app.shiv.domain.actions.ActionTier
import app.shiv.domain.actions.AuthorizedActionExecutor
import app.shiv.domain.integration.ReminderSchedulingPort
import app.shiv.domain.model.EventRecord
import app.shiv.domain.model.PendingEventRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PendingEventConfirmerTest {
    private lateinit var database: ShivDatabase
    private lateinit var repository: RoomShivViewRepository
    private val candidate = PendingEventRecord(id = "candidate-1", title = "Appointment", startsAt = 2_000L)
    private val request = ActionAuthorizationRequest(
        actionType = "CREATE_EVENT",
        arguments = mapOf("id" to candidate.id, "title" to candidate.title, "startsAt" to candidate.startsAt.toString()),
        configuredTier = ActionTier.FREE.name,
        minimumTier = ActionTier.FREE,
        confirmed = true
    )
    private var scheduled = 0
    private val reminders = object : ReminderSchedulingPort {
        override fun schedule(event: EventRecord, leadMinutes: Int) { scheduled++ }
        override fun cancel(event: EventRecord) = Unit
    }

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, ShivDatabase::class.java).build()
        repository = RoomShivViewRepository(database)
        scheduled = 0
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun failedStartedAuditRetainsCandidateWithoutEventOrReminderAndShowsError() = runBlocking {
        repository.savePendingEvent(candidate)
        val failing = confirmer(ActionAuditRepository { entry ->
            if (entry.outcome == ActionExecutionStatus.STARTED.name) error("Audit write failed")
            repository.logAction(entry)
        })

        val report = failing.confirm(candidate, request, 10)!!

        assertEquals(ActionExecutionStatus.AUDIT_UNAVAILABLE, report.status)
        assertFalse(report.dispatchAttempted)
        assertEquals(listOf(candidate), repository.pendingEvents(0).first())
        assertTrue(repository.events(0).first().isEmpty())
        assertEquals(0, scheduled)
        assertTrue(repository.messages().first().single().text.contains("couldn't securely record"))

        val retry = confirmer(ActionAuditRepository(repository::logAction))
        assertEquals(ActionExecutionStatus.SUCCEEDED, retry.confirm(candidate, request, 10)?.status)
        assertTrue(repository.pendingEvents(0).first().isEmpty())
        assertEquals(1, repository.events(0).first().size)
        assertEquals(1, scheduled)
    }

    @Test
    fun completedDispatchWithFailedFinalAuditCannotOfferDuplicateRetry() = runBlocking {
        repository.savePendingEvent(candidate)
        val failing = confirmer(ActionAuditRepository { entry ->
            if (entry.outcome == ActionExecutionStatus.SUCCEEDED.name) error("Final audit write failed")
            repository.logAction(entry)
        })

        val report = failing.confirm(candidate, request, 10)!!

        assertEquals(ActionExecutionStatus.AUDIT_UNAVAILABLE, report.status)
        assertTrue(report.dispatched)
        assertTrue(repository.pendingEvents(0).first().isEmpty())
        assertEquals(1, repository.events(0).first().size)
        assertEquals(1, scheduled)
        assertTrue(repository.messages().first().single().text.contains("record unavailable"))
    }

    @Test
    fun attemptedDispatchFailureClearsCandidateToAvoidDuplicateAction() = runBlocking {
        repository.savePendingEvent(candidate)
        val failingReminders = object : ReminderSchedulingPort {
            override fun schedule(event: EventRecord, leadMinutes: Int): Unit = error("Scheduling failed")
            override fun cancel(event: EventRecord) = Unit
        }
        val confirmer = confirmer(ActionAuditRepository(repository::logAction), failingReminders)

        val report = confirmer.confirm(candidate, request, 10)!!

        assertEquals(ActionExecutionStatus.FAILED, report.status)
        assertTrue(report.dispatchAttempted)
        assertTrue(repository.pendingEvents(0).first().isEmpty())
        assertEquals(1, repository.events(0).first().size)
        assertTrue(repository.messages().first().single().text.contains("did not finish cleanly"))
    }

    private fun confirmer(
        audit: ActionAuditRepository,
        reminderPort: ReminderSchedulingPort = reminders
    ) = PendingEventConfirmer(
        repository = repository,
        executor = AuthorizedActionExecutor(audit, clock = { 1_000L }),
        reminders = reminderPort,
        failureText = { report ->
            when {
                report.dispatched -> "The action completed, but its record unavailable"
                report.dispatchAttempted -> "The action did not finish cleanly"
                else -> "I couldn't securely record the action"
            }
        },
        nowMillis = { 1_000L }
    )
}
