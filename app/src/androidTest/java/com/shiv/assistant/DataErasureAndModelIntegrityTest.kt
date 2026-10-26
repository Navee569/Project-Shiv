package com.shiv.assistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shiv.assistant.ai.ModelIntegrity
import com.shiv.assistant.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DataErasureAndModelIntegrityTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun correctedFactsCannotReappearInVaultIndex() = runBlocking {
        val original="test-note-"+UUID.randomUUID().toString()
        val revised="corrected-note-"+UUID.randomUUID().toString()
        val vault=ShivVault(context)
        vault.addNote(original)
        val repo=ShivRepository(context)
        val fact=repo.remember(original)
        val correction=repo.correct(fact.id,revised)
        assertFalse(vault.notes().contains(original))
        assertTrue(vault.notes().contains(revised))
        assertNull(EncryptedDatabase.open(context).dao().memory(fact.id))
        assertEquals(correction.id,EncryptedDatabase.open(context).dao().memory(correction.id)?.id)
        repo.forget(correction.id)
        assertFalse(vault.notes().contains(revised))
        assertNull(EncryptedDatabase.open(context).dao().memory(correction.id))
    }

    @Test fun clearingChatErasesBothEncryptedRepresentations() = runBlocking {
        val controller=ShivController(context)
        val vault=ShivVault(context)
        val marker="private-chat-"+UUID.randomUUID().toString()
        vault.addChat("user",marker)
        val dao=EncryptedDatabase.open(context).dao()
        dao.putChat(ChatRecord(UUID.randomUUID().toString(),"user",marker))
        controller.clearHistory()
        assertEquals(0,vault.history().size)
        assertEquals(0,dao.recentChats(1000).size)
    }

    @Test fun modelHashAndSizeMustMatchOriginalFile() {
        val model=File(context.cacheDir,"integrity-"+UUID.randomUUID()+".litertlm")
        try {
            val bytes=ByteArray(8192){(it%251).toByte()}
            model.writeBytes(bytes)
            val hash=MessageDigest.getInstance("SHA-256")
                .digest(bytes).joinToString(""){"%02x".format(it)}
            assertTrue(ModelIntegrity.valid(model,bytes.size.toLong(),hash))
            assertFalse(ModelIntegrity.valid(model,bytes.size.toLong()+1,hash))
            model.appendBytes(byteArrayOf(1))
            assertFalse(ModelIntegrity.valid(model,bytes.size.toLong(),hash))
        } finally {model.delete()}
    }
}
