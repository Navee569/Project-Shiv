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

/** Uses a separate throwaway encrypted DB; NEVER wipes the owner or shared test DB. */
@RunWith(AndroidJUnit4::class)
class OwnerDataRetentionInstrumentedTest {
    private val context:Context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun personalRecordsEraseAtomicallyWithoutRemovingModelMetadata()=runBlocking {
        System.loadLibrary("sqlcipher")
        val name="shiv-wipe-test-"+UUID.randomUUID()+".db"
        val passphrase=ByteArray(32).also{SecureRandom().nextBytes(it)}
        val db=Room.databaseBuilder(context,ShivDatabase::class.java,name)
            .openHelperFactory(SupportOpenHelperFactory(passphrase)).build()
        try {
            val dao=db.dao()
            dao.putVault(VaultEntry("api_secret","secret-for-test"))
            dao.putMemory(MemoryRecord("secret-memory","private","FACT","owner"))
            dao.putChat(ChatRecord("chat-1","user","private"))
            dao.putEvent(EventRecord("meeting","private",System.currentTimeMillis()+60000,null,"owner"))
            dao.putModel(ModelRecord("test-model","public-model","router",
                "/not/real","a".repeat(64),4096L,"litertlm"))
            assertEquals(1,dao.recentChats(10).size)
            dao.eraseOwnerRecords()
            assertNull(dao.vault("api_secret"))
            assertNull(dao.memory("secret-memory"))
            assertTrue(dao.recentChats(10).isEmpty())
            assertTrue(dao.events(0,Long.MAX_VALUE).isEmpty())
            assertEquals(1,dao.models().size)
            assertEquals("public-model",dao.models().single().name)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
    @Test fun expiryDeletesPastMemoryButNotFutureOwnerFacts()=runBlocking {
        System.loadLibrary("sqlcipher")
        val name="shiv-ttl-test-"+UUID.randomUUID()+".db"
        val db=Room.databaseBuilder(context,ShivDatabase::class.java,name)
            .openHelperFactory(SupportOpenHelperFactory(
                ByteArray(32).also{SecureRandom().nextBytes(it)})).build()
        try {
            val now=System.currentTimeMillis()
            db.dao().putMemory(MemoryRecord("expired","old","FACT","owner",expiresAt=now-100))
            db.dao().putMemory(MemoryRecord("future","keep","FACT","owner",expiresAt=now+60000))
            assertEquals(1,db.dao().purgeExpiredMemories(now))
            assertNull(db.dao().memory("expired"))
            assertNotNull(db.dao().memory("future"))
            assertEquals(0,db.dao().purgeExpiredMemories(now))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
