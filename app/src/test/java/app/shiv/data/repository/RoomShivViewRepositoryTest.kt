package app.shiv.data.repository

import app.shiv.data.local.*

import android.content.Context
import androidx.room.Room
import app.shiv.ActionLogEntry
import app.shiv.ActionTierSetting
import app.shiv.ChatMessage
import app.shiv.EventRecord
import app.shiv.MemoryFact
import app.shiv.PendingEventRecord
import app.shiv.ReminderRule
import app.shiv.ReplyDraft
import app.shiv.ShivSettings
import app.shiv.UserProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class RoomShivViewRepositoryTest {
    private lateinit var database: ShivDatabase
    private lateinit var repository: RoomShivViewRepository

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, ShivDatabase::class.java).build()
        repository = RoomShivViewRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun domainRecordsRoundTripThroughRoomRepository() = runBlocking {
        val message = ChatMessage(id = "message", text = "Hello", fromUser = true, createdAt = 10L, tier = "FREE")
        val profile = UserProfile(name = "Owner", notes = "Local")
        val memory = MemoryFact(id = "memory", text = "Prefers quiet mornings", createdAt = 11L)
        val event = EventRecord(id = "meeting", title = "Meeting", startsAt = 100L, source = "CHAT")
        val pending = PendingEventRecord(id = "pending", title = "Possible meeting", startsAt = 120L)
        val action = ActionLogEntry(
            id = "action",
            action = "Action CREATE_EVENT",
            tier = "ACT_THEN_TELL",
            outcome = "SUCCEEDED",
            createdAt = 12L,
            actionType = "CREATE_EVENT",
            arguments = mapOf("id" to "event-1", "title" to "Meet \"Raj\"", "startsAt" to "1700000000000"),
            permissionState = "NOT_REQUIRED",
            confirmationState = "CONFIRMED",
            result = "Event saved",
            undoInfo = "DELETE_EVENT:event-1"
        )
        val rule = ReminderRule("lead", "15", 13L)
        val draft = ReplyDraft(id = "draft", person = "Raj", body = "See you", createdAt = 14L)
        val tier = ActionTierSetting("CREATE_EVENT", "ASK_FIRST")
        val settings = ShivSettings(proactiveEnabled = false, maxDailyInterruptions = 2)

        repository.addMessage(message)
        repository.saveProfile(profile)
        repository.saveMemory(memory)
        repository.saveEvent(event)
        repository.savePendingEvent(pending)
        repository.logAction(action)
        repository.saveRule(rule)
        repository.saveDraft(draft)
        repository.saveActionTier(tier)
        repository.saveSettings(settings)

        assertEquals(listOf(message), repository.messages().first())
        assertEquals(profile, repository.profile().first())
        assertEquals(listOf(memory), repository.memories().first())
        assertEquals(listOf(event), repository.events(0).first())
        assertEquals(listOf(pending), repository.pendingEvents(0).first())
        assertEquals(listOf(action), repository.actionLog().first())
        assertEquals(rule, repository.reminderRule("lead"))
        assertEquals(listOf(draft), repository.drafts().first())
        assertEquals(listOf(tier), repository.actionTiers().first())
        assertEquals(settings, repository.settings().first())

        repository.deleteMemory(memory.id)
        repository.deletePendingEvent(pending.id)
        assertEquals(emptyList<MemoryFact>(), repository.memories().first())
        assertEquals(emptyList<PendingEventRecord>(), repository.pendingEvents(0).first())
    }

    @Test
    fun eventQueriesAndDeletionPreserveSourceAndTimeBoundaries() = runBlocking {
        val oldCalendar = EventRecord(id = "old-calendar", title = "Old", startsAt = 9L, source = "CALENDAR")
        val calendar = EventRecord(id = "calendar", title = "Calendar", startsAt = 10L, source = "CALENDAR")
        val chat = EventRecord(id = "chat", title = "Chat", startsAt = 20L, source = "CHAT")
        repository.saveEvent(oldCalendar)
        repository.saveEvent(calendar)
        repository.saveEvent(chat)

        assertEquals(listOf(calendar, chat), repository.upcomingEvents(10L))
        assertEquals(listOf(calendar, oldCalendar), repository.calendarEvents().sortedBy { it.id })

        repository.deleteEvent(calendar.id)
        assertEquals(listOf(oldCalendar, chat), repository.events(0L).first())
        repository.clearCalendarEvents()
        assertEquals(listOf(chat), repository.upcomingEvents(0L))
        assertEquals(emptyList<EventRecord>(), repository.calendarEvents())
    }

    @Test
    fun calendarReplacementReplacesOnlyCalendarRows() = runBlocking {
        val previousCalendar = EventRecord(id = "calendar-old", title = "Old calendar", startsAt = 100L, source = "CALENDAR")
        val personalEvent = EventRecord(id = "personal", title = "Personal", startsAt = 200L, source = "CHAT")
        val importedCalendar = EventRecord(id = "calendar-new", title = "New calendar", startsAt = 300L, source = "CALENDAR")
        repository.saveEvent(previousCalendar)
        repository.saveEvent(personalEvent)

        repository.replaceCalendarEvents(listOf(importedCalendar))

        assertEquals(listOf(importedCalendar), repository.calendarEvents())
        assertEquals(listOf(personalEvent, importedCalendar), repository.upcomingEvents(0))
    }

    @Test
    fun oneShotLookupsAndClearOperationsCoverEveryRepositoryRecord() = runBlocking {
        val message = ChatMessage(id = "message", text = "Hello", fromUser = true)
        val profile = UserProfile(name = "Owner", notes = "Local")
        val memory = MemoryFact(id = "memory", text = "Likes tea")
        val event = EventRecord(id = "event", title = "Call", startsAt = 100L)
        val pending = PendingEventRecord(id = "pending", title = "Maybe", startsAt = 200L)
        val action = ActionLogEntry(id = "action", action = "Saved", tier = "FREE", outcome = "Local")
        val rule = ReminderRule("lead", "15")
        val draft = ReplyDraft(id = "draft", person = "Raj", body = "Hello")
        val tier = ActionTierSetting("CREATE_EVENT", "ASK_FIRST")
        val settings = ShivSettings(proactiveEnabled = false)
        repository.addMessage(message)
        repository.saveProfile(profile)
        repository.saveMemory(memory)
        repository.saveEvent(event)
        repository.savePendingEvent(pending)
        repository.logAction(action)
        repository.saveRule(rule)
        repository.saveDraft(draft)
        repository.saveActionTier(tier)
        repository.saveSettings(settings)

        assertEquals(profile, repository.profileOnce())
        assertEquals(settings, repository.settingsOnce())

        repository.clearMessages()
        repository.clearProfile()
        repository.clearMemories()
        repository.clearEvents()
        repository.clearPendingEvents()
        repository.clearLog()
        repository.clearRules()
        repository.clearDrafts()
        repository.clearActionTiers()
        repository.clearSettings()

        assertEquals(emptyList<ChatMessage>(), repository.messages().first())
        assertNull(repository.profileOnce())
        assertEquals(emptyList<MemoryFact>(), repository.memories().first())
        assertEquals(emptyList<EventRecord>(), repository.upcomingEvents(0L))
        assertEquals(emptyList<PendingEventRecord>(), repository.pendingEvents(0L).first())
        assertEquals(emptyList<ActionLogEntry>(), repository.actionLog().first())
        assertNull(repository.reminderRule("lead"))
        assertEquals(emptyList<ReplyDraft>(), repository.drafts().first())
        assertEquals(emptyList<ActionTierSetting>(), repository.actionTiers().first())
        assertNull(repository.settingsOnce())
    }
}
