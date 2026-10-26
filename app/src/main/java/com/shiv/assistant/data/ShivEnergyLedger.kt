package com.shiv.assistant.data

import org.json.JSONObject
import java.time.LocalDate

/** Counts completed real operations; seconds are measured only by callers, never estimated as observed. */
class ShivEnergyLedger(private val vault: ShivVault) {
    @Synchronized
    fun record(feature: String, durationMs: Long = 0) {
        require(durationMs >= 0)
        val day = LocalDate.now().toString()
        val json = JSONObject(vault.get("energy_ledger") ?: "{}")
        if (json.optString("date") != day) {
            json.put("date", day)
            json.put("features", JSONObject())
        }
        val features = json.getJSONObject("features")
        val item = features.optJSONObject(feature) ?: JSONObject()
        item.put("count", item.optInt("count") + 1)
        item.put("measuredMs", item.optLong("measuredMs") + durationMs)
        features.put(feature, item)
        vault.put("energy_ledger", json.toString())
    }
    /** Measured local model usage recorded on this phone today, rounded up to seconds.
     * Load time is included because it also consumes compute resources.
     * A new day always starts with zero usage; stale ledger entries never leak forward.
     */
    @Synchronized
    fun modelSecondsToday(): Int {
        val json = JSONObject(vault.get("energy_ledger") ?: "{}")
        if (json.optString("date") != LocalDate.now().toString()) return 0
        val features = json.optJSONObject("features") ?: return 0
        var milliseconds = 0L
        for (key in listOf("local_inference", "local_model_load")) {
            val value = features.optJSONObject(key)?.optLong("measuredMs") ?: 0L
            if (value > 0L) milliseconds =
                if (Long.MAX_VALUE - milliseconds < value) Long.MAX_VALUE
                else milliseconds + value
        }
        return ((milliseconds / 1000L) + if (milliseconds % 1000L > 0L) 1L else 0L)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun report(): String {
        val json = JSONObject(vault.get("energy_ledger") ?: "{}")
        return if (json.optString("date") != LocalDate.now().toString()) "No recorded activity today."
        else json.toString(2)
    }
}
