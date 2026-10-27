package com.shiv.assistant.automation

import android.content.Context
import androidx.work.*
import com.shiv.assistant.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.*
import kotlinx.coroutines.CancellationException

/** Durable encrypted queue, restarted by WorkManager after process death. */
class SignalWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result=withContext(Dispatchers.IO) {
        val dao=EncryptedDatabase.open(applicationContext).dao()
        dao.recoverInFlight()
        var examined=0
        while(examined<30) {
            val signal=dao.nextSignal()?:break
            if(dao.claim(signal.id)==0)continue
            try {
                val decision=if(signal.source=="calendar-change") {
                    DecisionTraceRecord(signal.id,signal.id,"OBSERVED",
                        "Phone calendar changed; review the Events screen if needed","FREE")
                } else when(val choice=CommitmentParser.analyze(signal.fact,ZoneId.systemDefault())) {
                    is CommitmentProposal.Ready -> DecisionTraceRecord(signal.id,signal.id,"ASK",
                        "Possible event: "+choice.title+" at "+choice.whenAt+"; owner must approve","ASK_FIRST")
                    is CommitmentProposal.Clarify -> DecisionTraceRecord(signal.id,signal.id,"ASK",choice.question,"ASK_FIRST")
                    CommitmentProposal.None -> DecisionTraceRecord(signal.id,signal.id,"STAY_QUIET","No supported commitment","FREE")
                }
                // Never write an actual calendar event or notify before owner approval.
                dao.finishSignal(signal.id,decision,System.currentTimeMillis())
                examined++
            } catch(cancelled:CancellationException) {
                throw cancelled
            } catch(e:Exception) {
                dao.markRetry(signal.id,e.javaClass.simpleName)
                return@withContext if(dao.signal(signal.id)?.status=="FAILED")
                    Result.success() else Result.retry()
            }
        }
        // A single worker intentionally limits work to 30 items. Without
        // rescheduling, high-volume input would stay pending indefinitely.
        if(dao.pendingCount()>0) schedule(applicationContext)
        Result.success()
    }
    companion object {
        suspend fun recoverPending(context:Context) {
            val dao=EncryptedDatabase.open(context).dao()
            dao.recoverInFlight()
            if(dao.pendingCount()>0)schedule(context)
        }
        fun schedule(context:Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "shiv-durable-signals",ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<SignalWorker>().build())
        }
    }
}
sealed interface CommitmentProposal {
    data class Ready(val title:String,val whenAt:ZonedDateTime,val confidence:Double):CommitmentProposal
    data class Clarify(val question:String):CommitmentProposal
    data object None:CommitmentProposal
}
object CommitmentParser {
    private val trigger=Regex("(?i)(meet|meeting|appointment|agreed|promised|told|call|ಭೇಟಿ|కలుస్తాను)")
    private val time=Regex("(?i)\\b(1[0-2]|0?[1-9])(?::([0-5][0-9]))?\\s*(am|pm)\\b")
    fun analyze(text:String,zone:ZoneId,now:ZonedDateTime=ZonedDateTime.now(zone)):CommitmentProposal {
        if(!trigger.containsMatchIn(text))return CommitmentProposal.None
        val match=time.find(text)?:return CommitmentProposal.Clarify("What time did you agree?")
        val hour=match.groupValues[1].toInt()%12+(if(match.groupValues[3].equals("pm",true))12 else 0)
        val minute=match.groupValues[2].toIntOrNull()?:0
        val day=DayOfWeek.entries.firstOrNull { text.contains(it.name,true) }
            ?:return CommitmentProposal.Clarify("Which day and date should I use?")
        var date=now.toLocalDate().with(java.time.temporal.TemporalAdjusters.nextOrSame(day))
        if(date.atTime(hour,minute).atZone(zone).isBefore(now))date=date.plusWeeks(1)
        return CommitmentProposal.Ready(text.take(200),date.atTime(hour,minute).atZone(zone),0.75)
    }
}
