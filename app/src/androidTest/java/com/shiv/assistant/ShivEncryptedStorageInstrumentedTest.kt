package com.shiv.assistant

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shiv.assistant.data.*
import com.shiv.assistant.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ShivEncryptedStorageInstrumentedTest {
    private fun app():Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun encryptionAndRoundTrip() = runBlocking {
        val dao=EncryptedDatabase.open(app()).dao()
        val id="instrumented-"+UUID.randomUUID()
        val text="private-"+UUID.randomUUID()
        dao.putMemory(MemoryRecord(id,text,"FACT","owner"))
        val loaded=dao.memory(id)
        assertEquals(text,loaded?.text)
        val db=app().getDatabasePath("shiv-private-v1.db")
        assertTrue("SQLCipher file should be created",db.isFile)
        val bytes=db.inputStream().use { it.readNBytes(16) }
        val plain="SQLite format 3".toByteArray()
        assertFalse("DB header must not be plain SQLite",bytes.take(plain.size)==plain.toList())
        dao.removeMemory(id)
        assertNull(dao.memory(id))
    }
    @Test fun vaultStoresEncryptedSecrets() {
        val vault=ShivVault(app())
        val key="test-secret-"+UUID.randomUUID()
        val secret="sensitive-"+UUID.randomUUID()
        vault.put(key,secret)
        assertEquals(secret,vault.get(key))
        val raw=app().getDatabasePath("shiv-private-v1.db").readBytes()
        val substring=secret.toByteArray()
        assertFalse("Secret leaked as plaintext database bytes",
            raw.asList().windowed(substring.size).any { it == substring.asList() })
    }
    @Test fun denylistedAppsAreRejectedBeforePersistence() = runBlocking {
        val repo=ShivRepository(app())
        assertNull(repo.incoming("notification","Bank transfer OTP 1234",
            10,packageName="com.phonepe.app"))
        assertNull(repo.incoming("accessibility","password",
            10,packageName="com.x8bit.bitwarden"))
    }
    @Test fun malformedToolsFailClosed() {
        val injection=StructuredToolParser.parse(
            """{"action":"SAVE_MEMORY","arguments":{"text":"hi","authority":"FREE"}}""")
        assertEquals(ToolAction.STAY_QUIET,injection.action)
        val unsupported=StructuredToolParser.parse(
            """{"action":"TRANSFER_MONEY","arguments":{"amount":"100"}}""")
        assertEquals(ToolAction.STAY_QUIET,unsupported.action)
    }
}
