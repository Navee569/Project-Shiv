package app.shiv

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/** Calls the production URI/decryption/parsing/Room path, including a real SQLite abort. */
@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {
    private lateinit var context: Context
    private lateinit var db: ShivDatabase
    private lateinit var backup: EncryptedBackup
    private lateinit var file: File
    private val password get() = "independent backup test".toCharArray()

    @Before fun setUp() = runBlocking<Unit> {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, ShivDatabase::class.java).build()
        backup = EncryptedBackup(context, db)
        file = File.createTempFile("restore-test-", ".shiv", context.cacheDir)
        val d = db.dao()
        d.saveProfile(UserProfile(name = "original", notes = "keep", updatedAt = 1))
        d.addMessage(ChatMessage("m", "keep", true, 1))
        d.saveMemory(MemoryFact("f", "keep", 1))
        d.saveEvent(EventRecord("e", "keep", 2, createdAt = 1))
        d.addPendingEvent(PendingEventRecord("p", "keep", 2, createdAt = 1))
        d.logAction(ActionLogEntry("a", "keep", "ASK_FIRST", "keep", 1))
        d.saveRule(ReminderRule("r", "keep", 1))
        d.saveDraft(ReplyDraft("d", "person", "keep", 1))
        d.saveActionTier(ActionTierSetting("event", "ASK_FIRST"))
        d.saveSettings(ShivSettings())
    }

    @After fun tearDown() { db.close(); file.delete() }

    private suspend fun snapshot(): List<Any?> {
        val d = db.dao()
        return listOf(d.profileOnce(), d.allMessages(), d.allMemories(), d.allEvents(),
            d.allPendingEvents(), d.allActions(), d.allRules(), d.allDrafts(),
            d.allActionTiers(), d.settingsOnce())
    }

    private suspend fun exported(): JSONObject {
        backup.export(Uri.fromFile(file), password)
        return JSONObject(String(decryptBackupPayload(file.readBytes(), password), Charsets.UTF_8))
    }

    private fun write(json: JSONObject) {
        val magic = "SHIVBK01".toByteArray(Charsets.US_ASCII)
        val salt = ByteArray(16) { it.toByte() }
        val iv = ByteArray(12) { (it + 1).toByte() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveBackupKey(password, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(magic)
        file.writeBytes(magic + salt + iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8)))
    }

    private suspend fun restoreMustFailWithoutChanges(before: List<Any?>) {
        val secret = password
        var failure: Exception? = null
        try { backup.restore(Uri.fromFile(file), secret) } catch (e: Exception) { failure = e }
        assertNotNull("restore must reject this input", failure)
        assertTrue(secret.all { it == '\u0000' })
        assertEquals("all ten affected tables must be unchanged", before, snapshot())
    }

    @Test fun malformedRecordsInEveryCollectionLeaveAllTablesUnchanged() = runBlocking<Unit> {
        val valid = exported()
        val before = snapshot()
        for (key in listOf("messages", "memories", "events", "pendingEvents", "actions", "rules", "drafts", "actionTiers")) {
            val invalid = JSONObject(valid.toString()).put(key, JSONArray().put(JSONObject()))
            write(invalid)
            restoreMustFailWithoutChanges(before)
        }
        for (value in listOf<Any>("true", 1, JSONObject.NULL)) {
            val invalid = JSONObject(valid.toString())
            invalid.getJSONObject("settings").put("proactiveEnabled", value)
            write(invalid)
            restoreMustFailWithoutChanges(before)
        }
    }

    @Test fun sqliteFailureAfterWritesRollsBackEveryAffectedTable() = runBlocking<Unit> {
        val json = exported()
        val before = snapshot()
        json.put("profile", JSONObject.NULL).put("messages", JSONArray())
        write(json)
        // Settings is the final insert, after all ten tables have been cleared and others written.
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_restore BEFORE INSERT ON shiv_settings BEGIN SELECT RAISE(ABORT, 'injected restore failure'); END")
        try { restoreMustFailWithoutChanges(before) } finally {
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_restore")
        }
    }

    @Test fun historicalNullProfileRestoresSuccessfully() = runBlocking<Unit> {
        val json = exported().put("profile", JSONObject.NULL)
        write(json)
        val before = snapshot()
        val secret = password
        backup.restore(Uri.fromFile(file), secret)
        assertNull(db.dao().profileOnce())
        assertEquals(before.drop(1), snapshot().drop(1))
        assertTrue(secret.all { it == '\u0000' })
    }

    @Test fun corruptTruncatedAndWrongPasswordInputsPreserveAllTables() = runBlocking<Unit> {
        exported()
        val packed = file.readBytes()
        val before = snapshot()
        for (bytes in listOf(packed.copyOf(3), packed.copyOf(packed.size - 1),
            packed.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })) {
            file.writeBytes(bytes)
            restoreMustFailWithoutChanges(before)
        }
        file.writeBytes(packed)
        val secret = "incorrect backup password".toCharArray()
        var failure: Exception? = null
        try { backup.restore(Uri.fromFile(file), secret) } catch (e: Exception) { failure = e }
        assertNotNull(failure)
        assertEquals(before, snapshot())
        assertTrue(secret.all { it == '\u0000' })
    }

    @Test fun destinationOpenFailureClearsPassphraseAndTemporaryOutput() = runBlocking<Unit> {
        val before = context.cacheDir.listFiles()!!.filter { it.name.startsWith("shiv-backup-") }.toSet()
        val directory = File(context.cacheDir, "backup-destination-directory").also { it.mkdirs() }
        val secret = password
        var failure: Exception? = null
        try { backup.export(Uri.fromFile(directory), secret) } catch (e: Exception) { failure = e }
        assertNotNull(failure)
        assertTrue(secret.all { it == '\u0000' })
        assertEquals(before, context.cacheDir.listFiles()!!.filter { it.name.startsWith("shiv-backup-") }.toSet())
        directory.delete()
    }

    @Test fun destinationWriteFailureDeletesPartialOutputAndClearsPassphrase() = runBlocking<Unit> {
        val uri = Uri.parse("content://app.shiv.test.failing-backup/output")
        val before = snapshot()
        val secret = password
        var failure: Exception? = null
        try { backup.export(uri, secret) } catch (e: Exception) { failure = e }
        assertNotNull("the destination write must fail", failure)
        assertTrue(secret.all { it == '\u0000' })
        context.contentResolver.query(uri, null, null, null, null)!!.use {
            assertTrue(it.moveToFirst())
            assertEquals("partial output must be deleted", 0, it.getInt(0))
        }
        assertEquals(before, snapshot())
        assertFalse(context.cacheDir.listFiles()!!.any { it.name.startsWith("shiv-backup-") })
    }
}
