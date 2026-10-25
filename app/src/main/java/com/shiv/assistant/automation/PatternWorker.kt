package com.shiv.assistant.automation

import android.content.Context
import androidx.work.*
import com.shiv.assistant.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Charging-only pattern learning; keeps hypotheses distinct from owner-confirmed facts. */
class PatternWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result=withContext(Dispatchers.IO) {
        if(!applicationContext.getSharedPreferences("shiv_learning",Context.MODE_PRIVATE)
            .getBoolean("owner_enabled",false))return@withContext Result.success()
        val dao=EncryptedDatabase.open(applicationContext).dao()
        val memories=dao.recentMemories(System.currentTimeMillis(),100)
        for(group in memories.filter{!it.explicit}.groupBy{it.text.trim().lowercase()}.values) {
            if(group.size<3)continue
            if(memories.any{it.explicit && group.any{candidate->candidate.id==it.supersedes}})continue
            val strongest=group.maxByOrNull{it.updatedAt}?:continue
            val digest=MessageDigest.getInstance("SHA-256").digest(strongest.text.toByteArray())
                .take(8).joinToString(""){"%02x".format(it)}
            dao.putBelief(BeliefRecord("pattern-$digest",strongest.text,
                (0.45+group.size*0.07).coerceAtMost(0.85),
                "Observed "+group.size+" times",strongest.source,null,System.currentTimeMillis()))
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
