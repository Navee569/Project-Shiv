package com.shiv.assistant.data

import com.shiv.assistant.domain.LocalComputeBudget
import org.json.JSONObject
import java.time.LocalDate

/**
 * Encrypted measured *wall-time* of LiteRT local load/infer operations only.
 * Values are not energy, token counts or estimated mAh.
 */
class LocalModelComputeLedger(private val vault:ShivVault) {
    @Synchronized
    fun budgetSeconds():Int =
        vault.get("local_budget_seconds")?.toIntOrNull()
            ?.takeIf { it in 60..3600 } ?: 600
    @Synchronized
    fun setBudgetSeconds(value:Int) {
        require(value in 60..3600)
        vault.put("local_budget_seconds",value.toString())
    }
    @Synchronized
    fun usedMillisecondsToday():Long {
        val value=JSONObject(vault.get("local_compute_usage")?:"{}")
        if(value.optString("day")!=LocalDate.now().toString())return 0L
        return value.optLong("milliseconds",0L).coerceAtLeast(0L)
    }
    @Synchronized
    fun record(milliseconds:Long) {
        if(milliseconds<=0)return
        val current=usedMillisecondsToday()
        val addition=milliseconds.coerceAtMost(24L*3_600_000L)
        val total=if(Long.MAX_VALUE-current<addition)Long.MAX_VALUE
            else current+addition
        vault.put("local_compute_usage",JSONObject()
            .put("day",LocalDate.now().toString())
            .put("milliseconds",total).toString())
    }
    fun leaseSeconds(desired:Int):Int=LocalComputeBudget.leaseSeconds(
        usedMillisecondsToday(),budgetSeconds(),desired)
    fun report():String {
        val used=usedMillisecondsToday()
        return "Local model wall time today: "+(used/1000L)+" s of "+
            budgetSeconds()+" s daily allowance. Not measured battery usage."
    }
}
