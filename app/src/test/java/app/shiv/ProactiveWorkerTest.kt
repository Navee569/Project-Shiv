package app.shiv

import app.shiv.data.local.*

import app.shiv.platform.ProactiveWorker
import app.shiv.platform.ProactiveNotifications
import app.shiv.platform.ProactiveDependencies
import app.shiv.domain.brain.ProactiveBrain
import app.shiv.domain.brain.AgentDecision
import app.shiv.domain.actions.ActionTier
import app.shiv.data.mapping.*
import android.content.Context
import androidx.room.Room
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import app.shiv.data.scheduling.RoomProactiveRepository
import java.util.Calendar
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

/** Exercises actual CoroutineWorker.doWork, Room queries/transactions and a fake Brain. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ProactiveWorkerTest {
    private lateinit var context: Context
    private lateinit var database: ShivDatabase
    private lateinit var notifications: FakeNotifications
    private lateinit var clock: AtomicLong
    private val dao get() = database.dao()

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, ShivDatabase::class.java).build()
        notifications = FakeNotifications()
        clock = AtomicLong(Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 24, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis)
        dao.saveSettings(ShivSettings())
    }

    @After
    fun tearDown() { database.close() }

    @Test // M0-001
    fun alertForBAccountsForBAndCannotRepeatB() = runBlocking {
        seed("A", "B")
        val brain = ProactiveBrain { alert("B") }
        assertSuccess(worker(brain).doWork())
        assertEquals(listOf("B"), notifications.events.toList())
        assertEquals(listOf("B"), dao.allActions().map { it.outcome })
        assertFalse(dao.wasProactiveSent("A"))
        assertTrue(dao.wasProactiveSent("B"))
        // B is no longer offered; even a fake/misbehaving Brain cannot select it again.
        assertSuccess(worker(brain).doWork())
        assertEquals(listOf("B"), notifications.events.toList())
    }

    @Test // M0-001 defensive identity validation
    fun missingAndUnknownEventIdsCannotDispatch() = runBlocking {
        seed("A")
        for (id in listOf(null, "not-offered")) {
            assertSuccess(worker(ProactiveBrain { alert(id) }).doWork())
        }
        assertTrue(notifications.events.isEmpty())
        assertTrue(dao.allActions().isEmpty())
    }

    @Test // M0-002
    fun overlappingWorkersWithOneDailySlotSendExactlyOneAlert() = runBlocking {
        seed("A", "B")
        repeat(2) { dao.logAction(ActionLogEntry(action = ProactiveWorker.SENT_ACTION,
            tier = ActionTier.ACT_THEN_TELL.name, outcome = "previous-$it", createdAt = clock.get())) }
        val sequence = AtomicInteger()
        val brain = GateBrain(2) { alert(if (sequence.getAndIncrement() == 0) "A" else "B") }
        val first = async(Dispatchers.Default) { worker(brain).doWork() }
        val second = async(Dispatchers.Default) { worker(brain).doWork() }
        withTimeout(10_000) { brain.entered.await() }
        brain.release.complete(Unit)
        withTimeout(10_000) { assertSuccess(first.await()); assertSuccess(second.await()) }
        assertEquals(1, notifications.events.size)
        assertEquals(3, dao.allActions().size)
        assertEquals(3, dao.countProactiveSince(clock.get()))
        assertTrue(dao.allActions().all { it.action == ProactiveWorker.SENT_ACTION })
    }

    @Test // M0-002 duplicate overlap with spare daily capacity
    fun overlappingWorkersForSameEventSendExactlyOnce() = runBlocking {
        seed("A")
        val brain = GateBrain(2) { alert("A") }
        val first = async(Dispatchers.Default) { worker(brain).doWork() }
        val second = async(Dispatchers.Default) { worker(brain).doWork() }
        withTimeout(10_000) { brain.entered.await() }
        brain.release.complete(Unit)
        withTimeout(10_000) { assertSuccess(first.await()); assertSuccess(second.await()) }
        assertEquals(listOf("A"), notifications.events.toList())
        assertEquals(1, dao.allActions().size)
    }

    @Test // M0-003
    fun disablingProactiveDuringInferenceCancelsAlert() = changedDuringInference {
        dao.saveSettings(ShivSettings(proactiveEnabled = false))
    }

    @Test // M0-003
    fun enteringQuietHoursDuringInferenceCancelsAlert() = changedDuringInference(eventDelayHours = 12) {
        clock.addAndGet(TimeUnit.HOURS.toMillis(10))
    }

    @Test // M0-003
    fun deletingEventDuringInferenceCancelsAlert() = changedDuringInference {
        dao.deleteEvent("A")
    }

    @Test // M0-003
    fun expiringEventDuringInferenceCancelsAlert() = changedDuringInference {
        clock.addAndGet(TimeUnit.HOURS.toMillis(2))
    }

    @Test // M0-003 stale event content must not be dispatched under the same id
    fun editingEventDuringInferenceCancelsAlert() = changedDuringInference {
        dao.saveEvent(requireNotNull(dao.eventById("A")).copy(title = "Changed meeting"))
    }

    @Test // M0-003 current policy/permission must also be checked
    fun revokingPermissionDuringInferenceCancelsAlert() = changedDuringInference {
        notifications.enabled = false
    }

    @Test // M0-003
    fun requiringConfirmationDuringInferenceCancelsAlert() = changedDuringInference {
        dao.saveActionTier(ActionTierSetting("SEND_NOTIFICATION", ActionTier.ASK_FIRST.name))
    }

    @Test // M0-004
    fun firstEightSentEventsDoNotHideNinthUnsentEvent() = runBlocking {
        seed(*(1..9).map { "event-$it" }.toTypedArray())
        for (i in 1..8) dao.logAction(ActionLogEntry(action = ProactiveWorker.SENT_ACTION,
            tier = ActionTier.ACT_THEN_TELL.name, outcome = "event-$i", createdAt = clock.get() - TimeUnit.DAYS.toMillis(1)))
        var snapshot = ""
        assertSuccess(worker(ProactiveBrain { snapshot = it; alert("event-9") }).doWork())
        assertTrue(snapshot.contains("id=event-9;"))
        assertFalse(snapshot.contains("id=event-1;"))
        assertEquals(listOf("event-9"), notifications.events.toList())
        assertTrue(dao.wasProactiveSent("event-9"))
    }

    @Test // Durable accounting when external dispatch success is uncertain
    fun failureAfterDispatchKeepsReservationAndPreventsRetryDuplicate() = runBlocking {
        seed("A")
        notifications.throwAfterDelivery = true
        val brain = ProactiveBrain { alert("A") }
        assertSuccess(worker(brain).doWork())
        assertEquals(listOf("A"), notifications.events.toList())
        assertEquals(ProactiveWorker.RESERVED_ACTION, dao.allActions().single().action)
        assertSuccess(worker(brain).doWork())
        assertEquals(listOf("A"), notifications.events.toList())
        assertEquals(1, dao.countProactiveSince(clock.get()))
    }

    private fun changedDuringInference(eventDelayHours: Long = 1, change: suspend () -> Unit) = runBlocking {
        seed("A", delayHours = eventDelayHours)
        val brain = GateBrain(1) { alert("A") }
        val running = async(Dispatchers.Default) { worker(brain).doWork() }
        withTimeout(10_000) { brain.entered.await() }
        change()
        brain.release.complete(Unit)
        withTimeout(10_000) { assertSuccess(running.await()) }
        assertTrue(notifications.events.isEmpty())
        assertTrue(dao.allActions().isEmpty())
    }

    private suspend fun seed(vararg ids: String, delayHours: Long = 1) {
        ids.forEachIndexed { index, id -> dao.saveEvent(EventRecord(id = id, title = "Meeting $id",
            startsAt = clock.get() + TimeUnit.HOURS.toMillis(delayHours) + index * 1000, createdAt = clock.get())) }
    }

    private fun worker(brain: ProactiveBrain): ProactiveWorker =
        TestListenableWorkerBuilder<ProactiveWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                    ProactiveWorker(appContext, workerParameters, ProactiveDependencies(RoomProactiveRepository(database), brain, notifications, clock::get))
            }).build()

    private fun alert(id: String?) = AgentDecision("send_notification", "", eventId = id,
        notificationTitle = "Upcoming meeting", notificationBody = "Your meeting is coming up.")

    private fun assertSuccess(result: ListenableWorker.Result) = assertEquals(ListenableWorker.Result.success(), result)

    private class GateBrain(expectedCalls: Int, private val decide: () -> AgentDecision) : ProactiveBrain {
        private val remaining = AtomicInteger(expectedCalls)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        override suspend fun decideProactive(context: String): AgentDecision {
            val decision = decide()
            if (remaining.decrementAndGet() == 0) entered.complete(Unit)
            release.await()
            return decision
        }
    }

    private class FakeNotifications : ProactiveNotifications {
        @Volatile var enabled = true
        var throwAfterDelivery = false
        val events = CopyOnWriteArrayList<String>()
        override fun allowed() = enabled
        override fun post(eventId: String, title: String, body: String): Boolean {
            if (!enabled) return false
            events.add(eventId)
            if (throwAfterDelivery) error("Simulated failure after external delivery")
            return true
        }
    }
}
