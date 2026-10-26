package com.shiv.assistant.integrations

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.shiv.assistant.domain.AppUsageObservation
import com.shiv.assistant.domain.UsageSummaryPolicy

/** On-demand statistics only; never polls, stores raw events or captures screen text. */
class ShivUsageReader(private val context: Context) {
    fun hasGrant(): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }
    fun pastDay(exclusions: Set<String> = emptySet()): List<AppUsageObservation> {
        if (!hasGrant()) return emptyList()
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val raw = manager.queryAndAggregateUsageStats(now-86400000L, now).values
            .map { AppUsageObservation(it.packageName.orEmpty(), it.totalTimeInForeground) }
        return UsageSummaryPolicy.allowed(raw, exclusions)
    }
}
