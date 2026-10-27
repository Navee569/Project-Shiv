package com.shiv.assistant

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shiv.assistant.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.io.RandomAccessFile

@RunWith(AndroidJUnit4::class)
class SqlCipherRobustnessTest {
    private val context:Context get()=InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun wrongPassphraseFailsWithoutDamagingOriginalData() = runBlocking {
        val db=EncryptedDatabase.open(context)
        val marker=UUID.randomUUID().toString()
        db.dao().putMemory(MemoryRecord(marker,"encrypted-marker","FACT","owner"))
        val wrong=Room.databaseBuilder(context,ShivDatabase::class.java,"shiv-private-v1.db")
            .openHelperFactory(SupportOpenHelperFactory(ByteArray(32){42.toByte()})).build()
        try {
            wrong.dao().recentMemories(System.currentTimeMillis(),2)
            fail("Encrypted DB must reject an invalid key")
        } catch (_: Exception) {
            // Expected SQLCipher rejection; AssertionError from fail() is not swallowed.
        } finally {wrong.close()}
        assertNotNull(db.dao().memory(marker))
        db.dao().removeMemory(marker)
    }

    @Test fun failedTransactionRollsBackAllChanges() = runBlocking {
        val db=EncryptedDatabase.open(context)
        val id=UUID.randomUUID().toString()
        try {
            db.withTransaction {
                db.dao().putMemory(MemoryRecord(id,"should roll back","FACT","owner"))
                throw IllegalStateException("Simulated power interruption before commit")
            }
            fail("Exception should escape transaction")
        } catch (_: IllegalStateException) {}
        assertNull(db.dao().memory(id))
    }

    @Test fun concurrentWritesAreNotLost() = runBlocking {
        val dao=EncryptedDatabase.open(context).dao()
        val prefix="concurrent-"+UUID.randomUUID().toString()
        val ids=(0 until 24).map{"$prefix-$it"}
        coroutineScope {
            ids.forEach { id ->
                launch(Dispatchers.IO) { dao.putMemory(MemoryRecord(id,"safe concurrent write","FACT","owner")) }
            }
        }
        ids.forEach { assertNotNull(dao.memory(it)) }
        ids.forEach { dao.removeMemory(it) }
    }

    @Test fun damagedCiphertextFailsClosedWithoutReplacingDatabase() = runBlocking {
        System.loadLibrary("sqlcipher")
        val name="corruption-${UUID.randomUUID()}.db"
        val key=ByteArray(32) { (it+23).toByte() }
        val file=context.getDatabasePath(name)
        val original=Room.databaseBuilder(context,ShivDatabase::class.java,name)
            .openHelperFactory(SupportOpenHelperFactory(key.copyOf())).build()
        try {
            original.dao().putMemory(MemoryRecord("proof","private data","FACT","owner"))
        } finally { original.close() }
        try {
            RandomAccessFile(file,"rw").use { it.seek(0);it.write(ByteArray(32) { 0x5a }) }
            val damaged=file.readBytes()
            val reopened=Room.databaseBuilder(context,ShivDatabase::class.java,name)
                .openHelperFactory(SupportOpenHelperFactory(key.copyOf())).build()
            try {
                try {
                    EncryptedDatabase.checkIntegrity(reopened)
                    fail("Corrupted encrypted header must be rejected")
                } catch (_:Exception) {}
            } finally { reopened.close() }
            assertArrayEquals("No plaintext or replacement database is created",damaged,file.readBytes())
        } finally { context.deleteDatabase(name) }
    }

    @Test fun recoveryRequiresExactOwnerPhraseBeforeAnyDeletion() = runBlocking {
        val file=context.getDatabasePath("shiv-private-v1.db")
        EncryptedDatabase.open(context).dao().pendingCount() // force existing file
        val before=file.length()
        try {
            EncryptedDatabase.resetAfterOwnerConfirmation(context,"wrong phrase")
            fail("Recovery must require owner confirmation")
        } catch (_:IllegalArgumentException) {}
        assertTrue(file.exists())
        assertEquals(before,file.length())
    }
}
