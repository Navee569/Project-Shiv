package app.shiv.data.repository

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
import app.shiv.ShivDatabase
import app.shiv.ShivSettings
import app.shiv.UserProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
        repository = RoomShivViewRepository(database.dao())
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
        val action = ActionLogEntry(id = "action", action = "Saved", tier = "FREE", outcome = "Local", createdAt = 12L)
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
}
