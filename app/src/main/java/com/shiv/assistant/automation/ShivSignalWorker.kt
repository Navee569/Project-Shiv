package com.shiv.assistant.automation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shiv.assistant.data.ShivSignalQueue
import com.shiv.assistant.data.ShivVault
import com.shiv.assistant.data.ShivKnowledge
import com.shiv.assistant.data.MemoryLayer
import com.shiv.assistant.domain.SignalFacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Replays unsent sanitized notification facts after app/process restart.
 * Does not generate model output or execute an external action.
 */
class ShivSignalWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val vault = ShivVault(applicationContext)
        if (vault.get("sensing_paused") == "yes" || vault.get("guest_mode") == "yes" ||
            vault.get("privacy_zone") == "yes") return@withContext Result.success()
        val queue = ShivSignalQueue(vault)
        val knowledge = ShivKnowledge(vault)
        try {
            repeat(100) {
                val item = queue.next() ?: return@withContext Result.success()
                val fact = SignalFacts.extract(item.title)
                if (fact != null) {
                    knowledge.save(fact.summary, MemoryLayer.EPISODIC,
                        source = item.source, explicit = false, confidence = fact.confidence)
                }
                // Evidence is stored before acknowledging queue item, so replays are idempotent.
                if (!queue.finish(item.id, true)) return@withContext Result.retry()
            }
            if (queue.next() == null) Result.success() else Result.retry()
        } catch (_: Exception) { Result.retry() }
    }
}
