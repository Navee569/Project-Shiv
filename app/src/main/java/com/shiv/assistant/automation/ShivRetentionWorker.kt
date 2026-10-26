package com.shiv.assistant.automation

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shiv.assistant.data.ShivRepository
import java.util.concurrent.TimeUnit

/** Physical SQLCipher record removal when owner-specified memory TTL expires.
 * Does not delete awaiting approval, undo traces or other pending decisions. */
class ShivRetentionWorker(context:Context, params:WorkerParameters):
    CoroutineWorker(context,params) {
    override suspend fun doWork():Result = try {
        ShivRepository(applicationContext).purgeExpired()
        Result.success()
    } catch (_:Exception) {
        Result.retry()
    }
    companion object {
        fun schedule(context:Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "shiv-expired-memory-cleanup",ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ShivRetentionWorker>(1,TimeUnit.DAYS)
                    .setConstraints(Constraints.Builder()
                        .setRequiresBatteryNotLow(true).build()).build())
        }
    }
}
