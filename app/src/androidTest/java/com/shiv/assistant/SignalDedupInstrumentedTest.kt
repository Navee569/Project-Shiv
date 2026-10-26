package com.shiv.assistant

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shiv.assistant.data.*
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SignalDedupInstrumentedTest {
    @Test fun identicalBackgroundRetriesAreDedupedWithinWindowButOtherSourcesSurvive()=runBlocking {
        val context:Context=InstrumentationRegistry.getInstrumentation().targetContext
        System.loadLibrary("sqlcipher")
        val name="shiv-dedup-"+UUID.randomUUID()+".db"
        val db=Room.databaseBuilder(context,ShivDatabase::class.java,name)
            .openHelperFactory(SupportOpenHelperFactory(
                ByteArray(32).also{SecureRandom().nextBytes(it)})).build()
        try {
            val dao=db.dao()
            val now=System.currentTimeMillis()
            val first=SignalRecord("first","notification:com.example.one","meeting",2,receivedAt=now)
            assertTrue(dao.enqueueIfNew(first,now-60000))
            assertFalse(dao.enqueueIfNew(first.copy(id="duplicate"),now-60000))
            assertTrue(dao.enqueueIfNew(first.copy(id="otherapp",
                source="notification:com.example.two"),now-60000))
            assertTrue(dao.enqueueIfNew(first.copy(id="later",
                receivedAt=now+60_000),now+1))
            assertEquals("first",dao.signal("first")?.id)
            assertNull(dao.signal("duplicate"))
            assertNotNull(dao.signal("otherapp"))
            assertNotNull(dao.signal("later"))
        } finally { db.close();context.deleteDatabase(name) }
    }
}
