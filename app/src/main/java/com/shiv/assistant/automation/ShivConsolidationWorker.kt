package com.shiv.assistant.automation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.shiv.assistant.data.ShivKnowledge
import com.shiv.assistant.data.ShivAgenda
import com.shiv.assistant.data.MemoryLayer
import com.shiv.assistant.domain.RoutineObservation
import com.shiv.assistant.domain.RoutineLearner
import com.shiv.assistant.data.ShivVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Charging-only compact/merge; explicit facts and corrections are never deleted. */
class ShivConsolidationWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val vault = ShivVault(applicationContext)
            if (vault.get("sensing_paused") == "yes" || vault.get("guest_mode") == "yes")
                return@withContext Result.success()
            val knowledge = ShivKnowledge(vault)
            knowledge.consolidate()
            if (vault.get("passive_learning_opt_in") == "yes") {
                val now = System.currentTimeMillis()
                val fromAgenda = ShivAgenda(vault).list()
                    .filter { it.completed && it.dueEpochMs <= now }
                    .map { RoutineObservation(it.id, it.title, it.dueEpochMs) }
                val routines = RoutineLearner.infer(fromAgenda, now)
                // Store only evidence-bearing unconfirmed hypotheses; never promote
                // them to explicit owner facts or change behavior without approval.
                for (routine in routines) {
                    val label = "Possible routine: " + routine.label +
                        " on weekday " + routine.weekday + " around " + routine.hour + ":00"
                    knowledge.save(label, MemoryLayer.PROCEDURAL,
                        source = "agenda evidence (" + routine.evidenceIds.size + " distinct days)",
                        explicit = false, confidence = routine.confidence)
                }
            }
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
}
object ShivConsolidation {
    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresCharging(true).setRequiresDeviceIdle(true).build()
        val request = PeriodicWorkRequestBuilder<ShivConsolidationWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "shiv_consolidation", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
