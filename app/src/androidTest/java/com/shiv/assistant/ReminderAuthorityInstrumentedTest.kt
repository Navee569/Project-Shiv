package com.shiv.assistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shiv.assistant.automation.ShivReminders
import com.shiv.assistant.data.EncryptedDatabase
import com.shiv.assistant.data.ShivVault
import com.shiv.assistant.domain.AuthorityTier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderAuthorityInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun reminderUndoChangesEncryptedStatusAndPreventsSecondUndo() = runBlocking {
        val id=ShivReminders.schedule(context,"remind me in 10 minutes to drink water")
        assertNotNull(id)
        val key=id.toString()
        val dao=EncryptedDatabase.open(context).dao()
        assertEquals("SCHEDULED",dao.reminder(key)?.status)
        assertTrue(ShivReminders.undo(context,key))
        assertEquals("CANCELLED",dao.reminder(key)?.status)
        assertFalse(ShivReminders.undo(context,key))
    }

    @Test fun reminderTierIsOwnerControlledAndPersistsAcrossControllers() = runBlocking {
        val owner=ShivController(context)
        owner.changeReminderTier(AuthorityTier.ACT_THEN_TELL)
        assertEquals(AuthorityTier.ACT_THEN_TELL,owner.reminderTier)
        val reopened=ShivController(context)
        assertEquals(AuthorityTier.ACT_THEN_TELL,reopened.reminderTier)
        // Restore safest default for subsequent emulator tests.
        reopened.changeReminderTier(AuthorityTier.ASK_FIRST)
    }

    @Test fun ownerMustApproveEachCalendarEventInsteadOfBulkAutoImport() = runBlocking {
        val phase=PhaseOneController(context,com.shiv.assistant.ai.ShivBrainHub(context))
        // No selection has been made. Guessing an ID is always rejected.
        phase.confirmCalendarImport("external-1-100")
        assertTrue(phase.issue?.contains("expired") == true)
    }
}
