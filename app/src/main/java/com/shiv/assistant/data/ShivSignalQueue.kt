package com.shiv.assistant.data

import com.shiv.assistant.domain.PrivacyPolicy
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SignalItem(
    val id: String,
    val source: String,
    val title: String,
    val createdAt: Long,
    val priority: Int,
    val important: Boolean,
    val attempts: Int
)
/**
 * Minimal encrypted, crash-resumable queue for sanitized facts.
 * This is deliberately not yet a Room/SQLCipher queue; migration is required before release.
 */
class ShivSignalQueue(private val vault: ShivVault) {
    @Synchronized
    fun enqueue(source: String, title: String?, extraDenylist: Set<String> = emptySet()): String? {
        if (vault.get("sensing_paused") == "yes" ||
            vault.get("guest_mode") == "yes" ||
            vault.get("privacy_zone") == "yes") return null
        val safe = PrivacyPolicy.safeNotificationTitle(source, title, extraDenylist) ?: return null
        val records = records()
        if (records.any { it.optString("source") == source && it.optString("title") == safe &&
                    it.optString("state") != "done" }) return null
        val important = Regex("(?i)\\b(meet|meeting|deadline|due|appointment|tomorrow|today|remind)\\b").containsMatchIn(safe)
        if (records.length() >= 500 && !important) return null
        val id = UUID.randomUUID().toString()
        records.put(JSONObject()
            .put("id", id).put("source", source.take(150))
            .put("title", safe).put("created", System.currentTimeMillis())
            .put("priority", if (important) 90 else 20).put("important", important)
            .put("attempts", 0).put("state", "pending"))
        vault.put("signal_queue", records.toString())
        return id
    }
    @Synchronized
    fun next(): SignalItem? {
        val array = records()
        val pending = (0 until array.length()).map { array.getJSONObject(it) }
            .filter { it.optString("state") == "pending" }
            .sortedWith(compareByDescending<JSONObject> { it.optInt("priority") }.thenBy { it.optLong("created") })
        val row = pending.firstOrNull() ?: return null
        return SignalItem(row.getString("id"), row.getString("source"), row.getString("title"),
            row.getLong("created"), row.getInt("priority"), row.getBoolean("important"),
            row.getInt("attempts"))
    }
    @Synchronized
    fun finish(id: String, success: Boolean): Boolean {
        val array = records()
        for (i in 0 until array.length()) {
            val row = array.getJSONObject(i)
            if (row.optString("id") != id) continue
            if (row.optString("state") == "done") return true
            val attempts = row.optInt("attempts") + 1
            row.put("attempts", attempts)
            row.put("state", if (success) "done" else "pending")
            // Compact only successfully handled low-priority records.
            val compact = JSONArray()
            for (j in 0 until array.length()) {
                val current = array.getJSONObject(j)
                if (current.optString("state") == "done" &&
                    !current.optBoolean("important") &&
                    System.currentTimeMillis() - current.optLong("created") > 86_400_000) continue
                compact.put(current)
            }
            vault.put("signal_queue", compact.toString())
            return true
        }
        return false
    }
    fun pendingCount(): Int = (0 until records().length()).count {
        records().getJSONObject(it).optString("state") == "pending"
    }
    private fun records(): JSONArray = JSONArray(vault.get("signal_queue") ?: "[]")
}
