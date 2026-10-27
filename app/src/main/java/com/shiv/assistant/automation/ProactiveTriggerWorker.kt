package com.shiv.assistant.automation

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import androidx.work.*
import com.shiv.assistant.data.EncryptedDatabase
import com.shiv.assistant.data.ShivRepository
import com.shiv.assistant.data.ShivVault
import com.shiv.assistant.integrations.UsageOverview
import com.shiv.assistant.domain.ProactiveTriggerPolicy
import com.shiv.assistant.awareness.AwarenessControl
import java.util.concurrent.TimeUnit

/** Durable, opt-in collection of task deadlines and a coarse long-idle unlock event. */
class ProactiveTriggerWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
      return try {
        val context=applicationContext
        val vault=ShivVault(context)
        if(vault.get("proactive_opt_in")!="yes" || AwarenessControl.isPaused(context))
            return Result.success()
        val now=System.currentTimeMillis()
        val dao=EncryptedDatabase.open(context).dao()
        val repository=ShivRepository(context)
        for(task in dao.pendingTasks().filter {
            ProactiveTriggerPolicy.approachingDeadline(it.dueAt,now)
        }.take(20)) {
            repository.incoming("task_deadline","Task deadline ${task.id} at ${task.dueAt}",3)
        }
        if(Build.VERSION.SDK_INT>=28 && UsageOverview(context).hasOwnerGrant()) {
            val manager=context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val events=manager.queryEvents(now-48*60*60_000L,now)
            val entry=UsageEvents.Event()
            val last=vault.get("last_long_idle_unlock_at")?.toLongOrNull() ?: 0L
            var shownAt:Long?=null
            var latestLongUnlock:Long?=null
            while(events!=null && events.hasNextEvent()) {
                events.getNextEvent(entry)
                when(entry.eventType) {
                    UsageEvents.Event.KEYGUARD_SHOWN -> shownAt=entry.timeStamp
                    UsageEvents.Event.KEYGUARD_HIDDEN -> {
                        val earlier=shownAt
                        if(ProactiveTriggerPolicy.longIdleUnlock(earlier,entry.timeStamp,last))
                            latestLongUnlock=entry.timeStamp
                        shownAt=null
                    }
                }
            }
            if(latestLongUnlock!=null && latestLongUnlock>last) {
                val saved=repository.incoming("long_idle_unlock",
                    "Long idle ended at $latestLongUnlock",2)
                if(saved!=null) vault.put("last_long_idle_unlock_at",latestLongUnlock.toString())
            }
        }
        Result.success()
      } catch (_:Exception) { Result.retry() }
    }

    companion object {
        private const val NAME="shiv-opt-in-proactive-triggers"
        fun updateRegistration(context:Context,enabled:Boolean) {
            val manager=WorkManager.getInstance(context)
            if(!enabled) { manager.cancelUniqueWork(NAME);return }
            manager.enqueueUniquePeriodicWork(NAME,ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ProactiveTriggerWorker>(15,TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                    .build())
        }
    }
}
