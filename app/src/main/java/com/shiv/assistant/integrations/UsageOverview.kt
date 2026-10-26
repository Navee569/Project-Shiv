package com.shiv.assistant.integrations

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.shiv.assistant.data.ShivVault
import com.shiv.assistant.domain.SourceExclusions
import com.shiv.assistant.domain.UsageObservation
import com.shiv.assistant.domain.UsageSummaryPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Explicit owner preview; no background polling, personal event stream or persisted result. */
class UsageOverview(private val context:Context) {
    fun hasOwnerGrant():Boolean {
        val ops=context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),context.packageName)==AppOpsManager.MODE_ALLOWED
    }
    suspend fun lastDay():List<UsageObservation> = withContext(Dispatchers.IO) {
        if(!hasOwnerGrant())return@withContext emptyList()
        val prefs=context.getSharedPreferences("shiv_awareness_control",Context.MODE_PRIVATE)
        if(prefs.getBoolean("paused",false))return@withContext emptyList()
        val exclusions=SourceExclusions.fromString(
            ShivVault(context).get("app_denied_packages"))
        val now=System.currentTimeMillis()
        val manager=context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val data=manager.queryAndAggregateUsageStats(now-86_400_000L,now)
            .values.map { UsageObservation(it.packageName.orEmpty(),it.totalTimeInForeground) }
        UsageSummaryPolicy.filtered(data,exclusions)
    }
}
