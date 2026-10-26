package com.shiv.assistant.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class DecisionTrace(val id: String, val action: String, val reason: String,
    val tier: String, val accepted: Boolean, val timestamp: Long)
/** Every external action decision is encrypted; no model can edit its trace. */
class ShivAuditTrail(private val vault: ShivVault) {
    @Synchronized
    fun add(action: String, reason: String, tier: String, accepted: Boolean): String {
        val id = UUID.randomUUID().toString()
        val source = JSONArray(vault.get("decision_traces") ?: "[]")
        val updated = JSONArray()
        for (i in maxOf(0, source.length() - 299) until source.length()) updated.put(source.get(i))
        updated.put(JSONObject().put("id", id).put("action", action.take(80))
            .put("reason", reason.take(300)).put("tier", tier)
            .put("accepted", accepted).put("timestamp", System.currentTimeMillis()))
        vault.put("decision_traces", updated.toString())
        return id
    }
    fun recent(): List<DecisionTrace> {
        val array = JSONArray(vault.get("decision_traces") ?: "[]")
        return (0 until array.length()).map { i ->
            val r = array.getJSONObject(i)
            DecisionTrace(r.getString("id"), r.getString("action"),
                r.getString("reason"), r.getString("tier"),
                r.getBoolean("accepted"), r.getLong("timestamp"))
        }.asReversed()
    }
}
