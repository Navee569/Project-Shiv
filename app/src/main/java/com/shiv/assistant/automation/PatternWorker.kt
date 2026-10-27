package com.shiv.assistant.automation

import android.content.Context
import androidx.work.*
import com.shiv.assistant.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import com.shiv.assistant.domain.PatternPolicy
import org.json.JSONArray
import kotlin.math.pow

/** Charging-only pattern learning; keeps hypotheses distinct from owner-confirmed facts. */
class PatternWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result=withContext(Dispatchers.IO) {
        if(!applicationContext.getSharedPreferences("shiv_learning",Context.MODE_PRIVATE)
            .getBoolean("owner_enabled",false))return@withContext Result.success()
        val dao=EncryptedDatabase.open(applicationContext).dao()
        val now=System.currentTimeMillis()
        val vault=ShivVault(applicationContext)
        val suppressedJson=JSONArray(vault.get("suppressed_pattern_ids")?:"[]")
        val suppressed=(0 until suppressedJson.length()).map { suppressedJson.optString(it) }.toSet()
        val prior=dao.beliefs().associateBy { it.id }
        val refreshed=mutableSetOf<String>()
        val signals=dao.recentProcessedSignals(now-30L*86_400_000L,1000)
        for(candidate in PatternPolicy.candidates(signals.map { it.source to it.receivedAt },now)) {
            if(candidate.id in suppressed || prior[candidate.id]?.correctedByOwner==true)continue
            dao.putBelief(BeliefRecord(candidate.id,candidate.hypothesis,candidate.confidence,
                candidate.evidence,"coarse-signal-time",candidate.lastSeenAt,now))
            refreshed.add(candidate.id)
        }
        val memories=dao.recentMemories(now,100)
        for(group in memories.filter{!it.explicit}.groupBy{it.text.trim().lowercase()}.values) {
            if(group.size<3)continue
            if(memories.any{it.explicit && group.any{candidate->candidate.id==it.supersedes}})continue
            val strongest=group.maxByOrNull{it.updatedAt}?:continue
            val digest=MessageDigest.getInstance("SHA-256").digest(strongest.text.toByteArray())
                .take(8).joinToString(""){"%02x".format(it)}
            val id="pattern-$digest"
            if(id in suppressed || prior[id]?.correctedByOwner==true)continue
            if(!applicationContext.getSharedPreferences("shiv_learning",Context.MODE_PRIVATE)
                .getBoolean("owner_enabled",false))return@withContext Result.success()
            dao.putBelief(BeliefRecord(id,strongest.text,
                (0.45+group.size*0.07).coerceAtMost(0.85),
                "Observed "+group.size+" times",strongest.source,strongest.updatedAt,now))
            refreshed.add(id)
        }
        for(old in prior.values.filter { it.id.startsWith("pattern-") && it.id !in refreshed &&
            !it.correctedByOwner }) {
            val elapsedDays=((now-old.updatedAt).coerceAtLeast(0L)/86_400_000.0)
            val confidence=old.confidence*0.92.pow(elapsedDays)
            if(confidence<0.5)dao.deleteBelief(old.id)
            else dao.putBelief(old.copy(confidence=confidence,updatedAt=now))
        }
        Result.success()
    }
    companion object {
        fun enable(context:Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "shiv-opt-in-pattern-learning",ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PatternWorker>(1,TimeUnit.DAYS)
                    .setConstraints(Constraints.Builder().setRequiresCharging(true).build()).build())
        }
    }
}
