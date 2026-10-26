package com.shiv.assistant

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shiv.assistant.data.*
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom
import java.util.UUID

/** Isolated throwaway SQLCipher database: no test may restore over real owner data. */
@RunWith(AndroidJUnit4::class)
class PortableArchiveInstrumentedTest {
    private val context:Context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun key()="long-portable-backup-passphrase".toCharArray()

    @Test fun authenticatedBackupRestoresPrivateRecordsWithoutRearmingActions()= runBlocking {
        System.loadLibrary("sqlcipher")
        val name="shiv-portable-test-"+UUID.randomUUID()+".db"
        val pass=ByteArray(32).also{ SecureRandom().nextBytes(it) }
        val db=Room.databaseBuilder(context,ShivDatabase::class.java,name)
            .openHelperFactory(SupportOpenHelperFactory(pass)).build()
        try {
            val dao=db.dao()
            dao.putMemory(MemoryRecord("m1","important private note","FACT","owner"))
            dao.putVault(VaultEntry("api_secret","test-provider-token"))
            dao.putVault(VaultEntry("cloud_consent","yes"))
            dao.putVault(VaultEntry("brain_mode","CLOUD"))
            dao.putReminder(ReminderRecord("r1","old reminder",System.currentTimeMillis()+60000,null))
            dao.putTier(PermissionTierRecord("REMINDER","ACT_THEN_TELL",System.currentTimeMillis()))
            dao.putModel(ModelRecord("model1","router","router",
                "/owner/private/model","a".repeat(64),4096L,"litertlm"))
            val archive=PortableOwnerArchive(context,db).exportEncrypted(key())
            assertFalse(String(archive,Charsets.ISO_8859_1).contains("important private note"))
            dao.removeMemory("m1")
            dao.putMemory(MemoryRecord("newer","new value","FACT","owner"))
            val restored=PortableOwnerArchive(context,db).restoreEncrypted(archive,key())
            assertTrue(restored>0)
            assertEquals("important private note",dao.memory("m1")?.text)
            assertNull(dao.memory("newer"))
            assertEquals("test-provider-token",dao.vault("api_secret")?.value)
            assertEquals("no",dao.vault("cloud_consent")?.value)
            assertEquals("LOCAL",dao.vault("brain_mode")?.value)
            assertEquals("RESTORED_INACTIVE",dao.reminder("r1")?.status)
            assertEquals("ASK_FIRST",dao.tier("REMINDER")?.tier)
            assertEquals("model1",dao.models().single().id)
        } finally {db.close();context.deleteDatabase(name)}
    }

    @Test fun invalidArchiveCannotDeleteExistingOwnerMemories()=runBlocking {
        System.loadLibrary("sqlcipher")
        val name="shiv-portable-rollback-"+UUID.randomUUID()+".db"
        val db=Room.databaseBuilder(context,ShivDatabase::class.java,name)
            .openHelperFactory(SupportOpenHelperFactory(
                ByteArray(32).also{SecureRandom().nextBytes(it)})).build()
        try {
            val dao=db.dao()
            dao.putMemory(MemoryRecord("keep","keep my records","FACT","owner"))
            val portable=PortableOwnerArchive(context,db)
            val archive=portable.exportEncrypted(key())
            assertTrue(runCatching {
                portable.restoreEncrypted(archive,"another-long-password".toCharArray())
            }.isFailure)
            assertNotNull(dao.memory("keep"))
            val text=PortableBackupCodec.decrypt(archive,key())
            val parsed=JSONObject(String(text,Charsets.UTF_8))
            parsed.getJSONObject("contents").getJSONArray("memories")
                .getJSONObject(0).remove("category")
            val invalid=PortableBackupCodec.encrypt(parsed.toString().toByteArray(),key())
            assertTrue(runCatching{portable.restoreEncrypted(invalid,key())}.isFailure)
            assertEquals("keep my records",dao.memory("keep")?.text)
        } finally {db.close();context.deleteDatabase(name)}
    }
}
