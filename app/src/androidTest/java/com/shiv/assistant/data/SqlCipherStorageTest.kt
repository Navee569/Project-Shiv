package com.shiv.assistant.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom
import java.util.UUID

/** Real Android instrumentation; cannot be executed by ordinary host JVM tests.
 * Uses a disposable test database and NEVER changes Shiv owner data. */
@RunWith(AndroidJUnit4::class)
class SqlCipherStorageTest {
    @Test fun ciphertextPersistsButPlainSqliteAndWrongKeyCannotRead() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        System.loadLibrary("sqlcipher")
        val name = "shiv-security-test-" + UUID.randomUUID() + ".db"
        val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val wrong = ByteArray(32).also { SecureRandom().nextBytes(it) }
        fun database(key: ByteArray): ShivRoomDatabase = Room.databaseBuilder(
            context, ShivRoomDatabase::class.java, name)
            .openHelperFactory(SupportOpenHelperFactory(key))
            .allowMainThreadQueries().build()
        try {
            database(secret.copyOf()).use { encrypted ->
                encrypted.entries().write(SecureEntry("test", "secret-not-plaintext"))
                assertEquals("secret-not-plaintext", encrypted.entries().read("test"))
            }
            val file = context.getDatabasePath(name)
            assertTrue(file.exists())
            val raw = file.readBytes()
            assertFalse(String(raw, Charsets.ISO_8859_1).contains("secret-not-plaintext"))
            val plain = runCatching {
                SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use {
                    it.rawQuery("SELECT value FROM secure_entries", null).use { cursor ->
                        cursor.moveToFirst()
                    }
                }
            }
            assertTrue("Plain SQLite must not decrypt SQLCipher file", plain.isFailure)
            val invalid = runCatching {
                database(wrong).use { it.entries().read("test") }
            }
            assertTrue("Wrong key must fail closed", invalid.isFailure)
            database(secret.copyOf()).use {
                assertEquals("secret-not-plaintext", it.entries().read("test"))
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
