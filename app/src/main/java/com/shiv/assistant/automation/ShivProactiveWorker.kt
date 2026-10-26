package com.shiv.assistant.automation

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shiv.assistant.data.ShivAgenda
import com.shiv.assistant.data.ShivFeedbackLedger
import com.shiv.assistant.domain.SelfReviewPolicy
import com.shiv.assistant.domain.ReviewReaction
import com.shiv.assistant.data.ShivAuditTrail
import com.shiv.assistant.data.ShivVault
import com.shiv.assistant.domain.AgendaSignal
import com.shiv.assistant.domain.ProactivePlanner
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Owner-opt-in, low-frequency, deterministic nudges: no microphone or AI model. */
class ShivProactiveWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val vault = ShivVault(applicationContext)
            if (vault.get("proactive_opt_in") != "yes" ||
                vault.get("sensing_paused") == "yes" ||
                vault.get("guest_mode") == "yes" || vault.get("privacy_zone") == "yes")
                return Result.success()
            if (Build.VERSION.SDK_INT >= 33 &&
                applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) return Result.success()
            val now = System.currentTimeMillis()
            val today = LocalDate.now().toString()
            val record = org.json.JSONObject(vault.get("proactive_delivery") ?: "{}")
            val delivered = if (record.optString("day") == today)
                record.optJSONArray("ids") ?: org.json.JSONArray()
            else org.json.JSONArray()
            val keys = (0 until delivered.length()).map { delivered.getString(it) }.toMutableSet()
            val agenda = ShivAgenda(vault).list()
            val recent = ShivFeedbackLedger(vault).lastDays()
                .map { ReviewReaction.valueOf(it.reaction.name) }
            val dailyCap = SelfReviewPolicy.suggestedDailyCap(recent)
            val proposals = ProactivePlanner.suggestions(
                agenda.map { AgendaSignal(it.id, it.title, it.dueEpochMs, it.completed) }, now)
            val local = ZonedDateTime.now()
            val brief = ProactivePlanner.briefKind(local)
            val selected = proposals.firstOrNull {
                ProactivePlanner.shouldDeliver(it, now, keys, keys.size, dailyCap).shouldNotify
            }
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
            manager.createNotificationChannel(NotificationChannel("shiv_proactive",
                "Shiv proactive insights", NotificationManager.IMPORTANCE_DEFAULT))
            val trace = ShivAuditTrail(vault)
            val identity: String
            val title: String
            val message: String
            if (selected != null) {
                identity = selected.id
                title = "Shiv • Upcoming plan"
                message = selected.title
            } else if (brief != null && keys.size < dailyCap &&
                local.hour !in 22..23 && local.hour >= 7 &&
                !keys.contains("brief:" + brief)) {
                identity = "brief:" + brief
                val scoped = agenda.filter { !it.completed &&
                    (if (brief == "morning") it.dueEpochMs >= now &&
                        it.dueEpochMs < now + 16 * 3_600_000L
                    else it.dueEpochMs >= now && it.dueEpochMs < now + 24 * 3_600_000L) }
                if (scoped.isEmpty()) return Result.success()
                title = if (brief == "morning") "Shiv • Morning agenda" else "Shiv • Evening review"
                message = scoped.size.toString() + " saved upcoming item(s). Open Shiv to review."
            } else return Result.success()
            // Persist the key before Android notification delivery; repeat workers never spam.
            keys.add(identity)
            vault.put("proactive_delivery", org.json.JSONObject().put("day", today)
                .put("ids", org.json.JSONArray(keys.toList())).toString())
            manager.notify(identity.hashCode(), NotificationCompat.Builder(applicationContext, "shiv_proactive")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title).setContentText(message.take(180))
                .setAutoCancel(true).build())
            trace.add("PROACTIVE", "Opt-in agenda fact: " + identity.take(100),
                "ACT_THEN_TELL", true)
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
}
object ShivProactive {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ShivProactiveWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build()).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "shiv_proactive", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
