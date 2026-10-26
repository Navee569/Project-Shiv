package com.shiv.assistant.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class OwnerReaction { ACTED, SNOOZED, DISMISSED, CORRECTED }
data class ReactionEntry(val traceId: String, val reaction: OwnerReaction, val at: Long)

/** Encrypted, owner-entered outcomes only; no guessed real-world completion. */
class ShivFeedbackLedger(private val vault: ShivVault) {
    @Synchronized fun record(traceId: String, reaction: OwnerReaction) {
        require(traceId.matches(Regex("[a-f0-9-]{36}")))
        val array = JSONArray(vault.get("owner_feedback") ?: "[]")
        val updated = JSONArray()
        val cutoff = System.currentTimeMillis() - 90L * 86400000L
        for (i in 0 until array.length()) {
            val row = array.getJSONObject(i)
            if (row.optLong("at") >= cutoff && row.optString("trace") != traceId)
                updated.put(row)
        }
        updated.put(JSONObject().put("trace", traceId).put("reaction", reaction.name)
            .put("at", System.currentTimeMillis()))
        vault.put("owner_feedback", updated.toString())
    }
    fun lastDays(days: Int = 7): List<ReactionEntry> {
        require(days in 1..90)
        val cutoff = System.currentTimeMillis() - days * 86400000L
        val array = JSONArray(vault.get("owner_feedback") ?: "[]")
        return (0 until array.length()).mapNotNull {
            val o = array.getJSONObject(it)
            val whenMs = o.optLong("at")
            val reaction = runCatching { OwnerReaction.valueOf(o.optString("reaction")) }.getOrNull()
            if (whenMs < cutoff || reaction == null) null
            else ReactionEntry(o.getString("trace"), reaction, whenMs)
        }
    }
    fun weeklyReport(): String {
        val list = lastDays()
        if (list.isEmpty()) return "No owner feedback for the last 7 days. Outcome accuracy is unmeasured."
        val counts = OwnerReaction.entries.associateWith { r -> list.count { it.reaction == r } }
        val issues = counts[OwnerReaction.CORRECTED]!! + counts[OwnerReaction.DISMISSED]!!
        return "Past 7 days • owner reactions: " + list.size +
            " | acted " + counts[OwnerReaction.ACTED] +
            " | snoozed " + counts[OwnerReaction.SNOOZED] +
            " | dismissed " + counts[OwnerReaction.DISMISSED] +
            " | corrected " + counts[OwnerReaction.CORRECTED] +
            if (issues >= 3) "\nSuggestion: consider fewer optional nudges." else ""
    }
}
