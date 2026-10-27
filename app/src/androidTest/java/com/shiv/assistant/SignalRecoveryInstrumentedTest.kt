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
class SignalRecoveryInstrumentedTest {
    @Test fun interruptedClaimReplaysToOneCommittedDecision()=runBlocking {
        val context:Context=InstrumentationRegistry.getInstrumentation().targetContext
        System.loadLibrary("sqlcipher")
        val name="shiv-recovery-"+UUID.randomUUID()+".db"
        val db=Room.databaseBuilder(context,ShivDatabase::class.java,name)
            .openHelperFactory(SupportOpenHelperFactory(ByteArray(32).also {
                SecureRandom().nextBytes(it)
            })).build()
        try {
            val dao=db.dao()
            val id=UUID.randomUUID().toString()
            dao.enqueue(SignalRecord(id,"owner_input","meet Raj Thursday 5 pm",5))
            assertEquals(1,dao.claim(id))
            assertEquals("PROCESSING",dao.signal(id)?.status)
            dao.recoverInFlight()
            assertEquals("RETRY",dao.signal(id)?.status)
            assertEquals(1,dao.claim(id))
            dao.finishSignal(id,DecisionTraceRecord(id,id,"ASK","Review event","ASK_FIRST"),
                System.currentTimeMillis())
            dao.recoverInFlight()
            assertEquals("PROCESSED",dao.signal(id)?.status)
            assertNull(dao.nextSignal())
            assertEquals(1,dao.traces(10).count { it.signalId==id })
        } finally { db.close();context.deleteDatabase(name) }
    }
}
