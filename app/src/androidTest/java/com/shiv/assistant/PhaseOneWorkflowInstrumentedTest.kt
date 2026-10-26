package com.shiv.assistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shiv.assistant.ai.ShivBrainHub
import com.shiv.assistant.automation.SignalWorker
import com.shiv.assistant.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PhaseOneWorkflowInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun failedSignalsRemainReviewableAndCanBeRetried() = runBlocking {
        val dao=EncryptedDatabase.open(context).dao()
        val id=UUID.randomUUID().toString()
        dao.enqueue(SignalRecord(id,"test","A retained failed observation",3,
            status="FAILED",attempts=8,lastError="transient"))
        assertTrue(dao.failedSignalCount()>=1)
        assertTrue(dao.retryFailedSignals()>=1)
        val retried=dao.signal(id)!!
        assertEquals("RETRY",retried.status)
        assertEquals(0,retried.attempts)
        assertNull(retried.lastError)
    }

    @Test fun backlogLargerThan30IsProcessedWithoutLostSignals() = runBlocking {
        val dao=EncryptedDatabase.open(context).dao()
        val keys=(0 until 37).map { UUID.randomUUID().toString() }
        for (id in keys) {
            dao.enqueue(SignalRecord(id,"test","An unrelated weather observation",3))
        }
        SignalWorker.schedule(context)
        withTimeout(90_000) {
            while(keys.any { dao.signal(it)?.status!="PROCESSED" }) delay(350)
        }
        keys.forEach { assertEquals("PROCESSED",dao.signal(it)?.status) }
    }

    @Test fun reviewAndApproveCreatesSingleEventAndReminder() = runBlocking {
        val dao=EncryptedDatabase.open(context).dao()
        val id=UUID.randomUUID().toString()
        val nextThursday=ZonedDateTime.now().with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.THURSDAY))
        val command="meet Raj Thursday 5 pm"
        dao.enqueue(SignalRecord(id,"owner_input",command,5,status="PROCESSED"))
        val trace=DecisionTraceRecord(id,id,"ASK","Possible event: $command at $nextThursday","ASK_FIRST")
        dao.trace(trace)
        val controller=PhaseOneController(context,ShivBrainHub(context))
        controller.approveCommitment(trace)
        assertEquals("APPROVED",dao.decision(id)?.decision)
        assertEquals(1,dao.events(0,Long.MAX_VALUE).count{it.id==id})
        assertNotNull(dao.reminder("event-$id"))
        assertEquals(1,dao.actions(100).count{it.id=="approval-$id" && it.confirmed})
        controller.approveCommitment(trace)
        assertEquals(1,dao.events(0,Long.MAX_VALUE).count{it.id==id})
    }

    @Test fun ownerUndoReversesApprovedEventAndReminderExactlyOnce() = runBlocking {
        val dao=EncryptedDatabase.open(context).dao()
        val id=UUID.randomUUID().toString()
        val command="meet Raj Thursday 5 pm"
        dao.enqueue(SignalRecord(id,"owner_input",command,5,status="PROCESSED"))
        val trace=DecisionTraceRecord(id,id,"ASK","Possible event: $command","ASK_FIRST")
        dao.trace(trace)
        val controller=PhaseOneController(context,ShivBrainHub(context))
        controller.approveCommitment(trace)
        assertEquals("APPROVED",dao.decision(id)?.decision)
        assertNotNull(dao.reminder("event-$id"))
        controller.undoApprovedCommitment(trace)
        assertEquals("UNDONE",dao.decision(id)?.decision)
        assertEquals("CANCELLED",dao.reminder("event-$id")?.status)
        assertTrue(dao.events(0L,Long.MAX_VALUE).none{it.id==id})
        assertEquals(1,dao.actions(100).count{it.id=="undo-$id"})
        controller.undoApprovedCommitment(trace)
        assertEquals(1,dao.actions(100).count{it.id=="undo-$id"})
    }

    @Test fun ownerCorrectionOverrulesOldMemoryAndDeletionWorks() = runBlocking {
        val repo=ShivRepository(context)
        val original=repo.remember("The appointment is on Friday")
        val revised=repo.correct(original.id,"The appointment is on Saturday")
        assertTrue(revised.explicit)
        assertEquals(original.id,revised.supersedes)
        assertEquals(1.0,revised.confidence,0.001)
        assertTrue(repo.search("appointment Saturday").any{it.id==revised.id})
        repo.forget(revised.id)
        assertFalse(repo.search("appointment Saturday").any{it.id==revised.id})
    }
    @Test fun repeatedExplicitMemoryIsStoredOnce() = runBlocking {
        val marker="Owner fact "+UUID.randomUUID()
        val repository=ShivRepository(context)
        val first=repository.remember(marker)
        val ids=coroutineScope {
            (0 until 8).map { async(Dispatchers.IO) { repository.remember(marker).id } }
                .awaitAll()
        }
        assertTrue(ids.all { it==first.id })
        assertEquals(1,repository.search(marker).count { it.id==first.id })
        val vault=ShivVault(context)
        vault.addNote(marker)
        vault.addNote(marker)
        assertEquals(1,vault.notes().count { it==marker })
        repository.forget(first.id)
    }

    @Test fun chatRefreshesEditedMemoriesBeforeEveryReply() = runBlocking {
        val controller=ShivController(context)
        val original=controller.vault.get("notes")
        val marker="owner-updated-memory-"+UUID.randomUUID()
        try {
            controller.vault.addNote(marker)
            controller.submit("What's on tomorrow?")
            assertTrue(controller.notes.contains(marker))
            controller.vault.put("notes",original ?: "[]")
            controller.submit("What's on tomorrow?")
            assertFalse(controller.notes.contains(marker))
        } finally {
            controller.vault.put("notes",original ?: "[]")
        }
    }

    @Test fun failedOwnerApprovedReminderIsLoggedAsFailure() = runBlocking {
        val controller=ShivController(context)
        controller.propose(com.shiv.assistant.domain.ShivPlanner.parse("remind impossible date"))
        controller.approve()
        val log=EncryptedDatabase.open(context).dao().actions(20)
            .first { it.type=="REMINDER" && it.result=="INVALID_DATE" }
        assertEquals("[REDACTED]",log.argsRedacted)
        assertTrue(log.confirmed)
    }
}
