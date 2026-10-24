package app.shiv.data.repository

import android.content.Context
import androidx.room.Room
import app.shiv.EventRecord
import app.shiv.MemoryFact
import app.shiv.ShivDatabase
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
    fun repositoryStreamsAndPersistsViewModelData() = runBlocking {
        val profile = UserProfile(name = "Owner", notes = "Local")
        repository.saveProfile(profile)
        assertEquals(profile, repository.profile().first())

        val memory = MemoryFact(text = "Prefers quiet mornings")
        repository.saveMemory(memory)
        assertEquals(listOf(memory), repository.memories().first())

        val event = EventRecord(id = "meeting", title = "Meeting", startsAt = 100L)
        repository.saveEvent(event)
        assertEquals(listOf(event), repository.upcomingEvents(0))

        repository.deleteMemory(memory.id)
        assertEquals(emptyList<MemoryFact>(), repository.memories().first())
    }
}
