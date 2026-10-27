package com.shiv.assistant.data

import android.content.Context
import com.shiv.assistant.automation.CommitmentParser
import com.shiv.assistant.automation.CommitmentProposal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

data class ApprovedReminder(val id:String,val dueAt:Long)

/** Validates current encrypted evidence before a decision becomes an event. */
class CommitmentDecisionRepository(context:Context) {
    private val dao=EncryptedDatabase.open(context).dao()
    suspend fun approve(traceId:String,signalId:String?):ApprovedReminder = withContext(Dispatchers.IO) {
        val current=dao.decision(traceId)?:error("Decision no longer exists")
        require(current.decision=="ASK") { "Already resolved" }
        val incoming=dao.signal(signalId?:error("Source signal missing"))
            ?:error("Source signal missing")
        require(incoming.status=="PROCESSED") { "Signal not safely processed" }
        val proposal=CommitmentParser.analyze(incoming.fact,ZoneId.systemDefault())
        require(proposal is CommitmentProposal.Ready) {
            "Commitment is ambiguous; provide day and time before approving."
        }
        val deadline=proposal.whenAt.toInstant().toEpochMilli()
        require(deadline>System.currentTimeMillis()) { "Event time has passed" }
        val event=EventRecord(incoming.id,proposal.title,deadline,null,incoming.source,
            proposal.confidence)
        val reminderId="event-"+incoming.id
        val reminderAt=(deadline-15*60_000L).coerceAtLeast(System.currentTimeMillis()+1000L)
        dao.confirmEvent(event,ReminderRecord(reminderId,
            "Upcoming: "+proposal.title.take(180),reminderAt,event.id),
            current.copy(decision="APPROVED",reason="Owner reviewed and approved event; "+
                "notification queued",policyTier="ASK_FIRST"),
            ActionLogRecord("approval-"+incoming.id,"CREATE_EVENT","[REDACTED]",
                "ASK_FIRST","OWNER_APPROVED",true,"EVENT_AND_REMINDER_SAVED","EVENT:"+event.id))
        ApprovedReminder(reminderId,reminderAt)
    }
    suspend fun undo(traceId:String):String? = withContext(Dispatchers.IO) {
        val current=dao.decision(traceId) ?: error("Decision not found")
        val eventId=current.signalId ?: error("Source event unavailable")
        val audit=ActionLogRecord("undo-"+eventId,"UNDO_EVENT","[REDACTED]",
            "ASK_FIRST","OWNER_REQUESTED",true,"EVENT_REMINDER_CANCELLED","NOT_AVAILABLE")
        if(dao.undoApprovedEvent(current.id,eventId,audit))eventId else null
    }
    suspend fun decline(traceId:String) = withContext(Dispatchers.IO) {
        val current=dao.decision(traceId)?:return@withContext
        if(current.decision=="ASK")dao.trace(current.copy(decision="DECLINED",
            reason="Owner declined the suggested commitment"))
    }
}
