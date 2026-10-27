package com.shiv.assistant

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shiv.assistant.data.LegacyVaultMigration
import com.shiv.assistant.data.ShivDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyVaultMigrationInstrumentedTest {
    @Test fun interruptedCopyRollsBackAndRestartDoesNotOverwriteOwnerEdits() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,ShivDatabase::class.java).build()
        try {
            val keys=setOf("alpha","beta")
            try {
                LegacyVaultMigration.copyAtomically(db,keys) { key ->
                    if(key=="beta")error("Simulated legacy read failure") else "old-alpha"
                }
                fail("Migration should fail before commit")
            } catch (_:IllegalStateException) {}
            assertNull(db.dao().vault("alpha"))
            assertNull(db.dao().vault(LegacyVaultMigration.MARKER))

            LegacyVaultMigration.copyAtomically(db,keys) { "original-$it" }
            assertEquals("original-alpha",db.dao().vault("alpha")?.value)
            db.dao().putVault(com.shiv.assistant.data.VaultEntry("alpha","owner-new-value"))
            LegacyVaultMigration.copyAtomically(db,keys) { "stale-$it" }
            assertEquals("owner-new-value",db.dao().vault("alpha")?.value)
        } finally { db.close() }
    }
}
