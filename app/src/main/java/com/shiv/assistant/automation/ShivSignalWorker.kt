package com.shiv.assistant.automation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shiv.assistant.data.ShivSignalQueue
import com.shiv.assistant.data.ShivVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Replays unsent sanitized notification facts after app/process restart.
 * Does not generate model output or execute an external action.
 */
class ShivSignalWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val queue = ShivSignalQueue(ShivVault(applicationContext))
        try {
            repeat(100) {
                val item = queue.next() ?: return@withContext Result.success()
                // Acknowledging the durable encrypted signal is safe; future AI routing must
                // be behind a separate deterministic authority check.
                if (!queue.finish(item.id, true)) return@withContext Result.retry()
            }
            if (queue.next() == null) Result.success() else Result.retry()
        } catch (_: Exception) { Result.retry() }
    }
}
