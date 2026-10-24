package app.shiv.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase as PlainSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.shiv.data.repository.RoomCaptureQueueRepository
import app.shiv.domain.model.CapturePriority
import app.shiv.domain.model.CaptureSignal
import app.shiv.platform.DatabaseKeyUnavailableException
import java.security.KeyStore
import java.security.SecureRandom
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedPersistenceInstrumentationTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun productionKeystoreAndSqlcipherOpenPathEncryptsAndReopensQueue() = runBlocking<Unit> {
        val name = "m3-encrypted-${System.nanoTime()}.db"
        val preferences = context.getSharedPreferences("shiv_secure", Context.MODE_PRIVATE)
        try {
            val database = ShivDatabase.openProduction(context, name)
            database.dao().saveProfile(DbUserProfile(name = "Owner", notes = "private profile"))
            val captures = RoomCaptureQueueRepository(database)
            assertEquals(true, captures.enqueue(CaptureSignal(
                id = "queued-reopen", payload = "private signal", source = "LISTENING",
                capturedAt = 123, priority = CapturePriority.HIGH
            )))
            val file = context.getDatabasePath(name)
            database.close()

            val plainReaderFailure = runCatching {
                PlainSQLiteDatabase.openDatabase(file.absolutePath, null, PlainSQLiteDatabase.OPEN_READONLY).use { raw ->
                    raw.rawQuery("SELECT name FROM sqlite_master", null).use { it.count }
                }
            }.exceptionOrNull()
            assertNotNull("Plain SQLite must not read the SQLCipher file", plainReaderFailure)

            val reopened = ShivDatabase.openProduction(context, name)
            assertEquals("private profile", reopened.dao().profileOnce()?.notes)
            val saved = RoomCaptureQueueRepository(reopened).get("queued-reopen")
            assertEquals("private signal", saved?.payload)
            assertEquals("LISTENING", saved?.source)
            assertEquals(123L, saved?.capturedAt)
            assertEquals(CapturePriority.HIGH, saved?.priority)
            reopened.close()

            val wrongKey = ByteArray(32).also(SecureRandom()::nextBytes)
            val wrongKeyDatabase = ShivDatabase.openEncrypted(context, name, wrongKey)
            assertThrows(Exception::class.java) { runBlocking { wrongKeyDatabase.dao().profileOnce() } }
            wrongKeyDatabase.close()

            // Simulate Keystore invalidation after the encrypted file and wrapped key exist.
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                deleteEntry("shiv_database_wrapping_key")
            }
            assertThrows(DatabaseKeyUnavailableException::class.java) {
                ShivDatabase.openProduction(context, name)
            }
        } finally {
            context.deleteDatabase(name)
            preferences.edit().remove("wrapped_db_key").commit()
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                if (containsAlias("shiv_database_wrapping_key")) deleteEntry("shiv_database_wrapping_key")
            }
        }
    }

    @Test
    fun encryptedVersionFiveDatabaseMigratesAndPreservesExistingRows() = runBlocking<Unit> {
        System.loadLibrary("sqlcipher")
        val name = "m3-migration-${System.nanoTime()}.db"
        val key = ByteArray(32).also(SecureRandom()::nextBytes)
        try {
            val schema = JSONObject(
                requireNotNull(javaClass.getResourceAsStream("/app.shiv.data.local.ShivDatabase/5.json"))
                    .bufferedReader().use { it.readText() }
            ).getJSONObject("database")
            val helper = SupportOpenHelperFactory(key).create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(name)
                    .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            val entities = schema.getJSONArray("entities")
                            for (i in 0 until entities.length()) {
                                val entity = entities.getJSONObject(i)
                                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                                val indices = entity.optJSONArray("indices") ?: continue
                                for (index in 0 until indices.length()) db.execSQL(indices.getJSONObject(index).getString("createSql"))
                            }
                            val setup = schema.getJSONArray("setupQueries")
                            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                        }
                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    })
                    .build()
            )
            helper.writableDatabase.use { old ->
                old.execSQL("INSERT INTO memories (id,text,createdAt) VALUES ('memory-old','Prefers quiet mornings',10)")
                old.execSQL("INSERT INTO events (id,title,startsAt,place,person,source,createdAt) VALUES ('event-old','Meeting',200,NULL,NULL,'CHAT',11)")
            }
            helper.close()

            val migrated = ShivDatabase.openEncrypted(context, name, key)
            assertEquals("Prefers quiet mornings", migrated.dao().allMemories().single().text)
            assertEquals("OWNER", migrated.dao().allMemories().single().source)
            assertEquals("Meeting", migrated.dao().eventById("event-old")?.title)
            assertEquals(emptyList<DbTaskRecord>(), migrated.dao().allTasks())
            migrated.close()
        } finally {
            context.deleteDatabase(name)
        }
    }
}
