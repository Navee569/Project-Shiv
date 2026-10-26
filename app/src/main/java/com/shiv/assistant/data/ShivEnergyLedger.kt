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
    fun report(): String {
        val json = JSONObject(vault.get("energy_ledger") ?: "{}")
        return if (json.length() == 0) "No recorded activity today."
        else json.toString(2)
    }
}
