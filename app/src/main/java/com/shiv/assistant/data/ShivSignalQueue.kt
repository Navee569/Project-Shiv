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
    companion object { private val processLock = Any() }
    fun enqueue(source: String, title: String?, extraDenylist: Set<String> = emptySet()): String? = synchronized(processLock) {
        if (vault.get("sensing_paused") == "yes" ||
            vault.get("guest_mode") == "yes" ||
            vault.get("privacy_zone") == "yes") return@synchronized null
        val safe = PrivacyPolicy.safeNotificationTitle(source, title, extraDenylist) ?: return@synchronized null
        val records = records()
        if ((0 until records.length()).any { i ->
                val row = records.getJSONObject(i)
                row.optString("source") == source && row.optString("title") == safe &&
                    row.optString("state") != "done"
            }) return@synchronized null
        val important = Regex("(?i)\\b(meet|meeting|deadline|due|appointment|tomorrow|today|remind|call|visit|interview)\\b|" +
            "\\b[0-2]?[0-9]:[0-5][0-9]\\b|\\b[0-9]{1,2}\\s*(?:am|pm)\\b|ನಾಳೆ|రేపు").containsMatchIn(safe)
        if (records.length() >= 500 && !important) {
            // Each permitted low-priority duplicate/drop is recorded without message content.
            val drops = JSONArray(vault.get("signal_drop_audit") ?: "[]")
            drops.put(JSONObject().put("at", System.currentTimeMillis())
                .put("reason", "low-priority queue cap"))
            vault.put("signal_drop_audit", drops.toString())
            return@synchronized null
        }
        val id = UUID.randomUUID().toString()
        records.put(JSONObject()
            .put("id", id).put("source", source.take(150))
            .put("title", safe).put("created", System.currentTimeMillis())
            .put("priority", if (important) 90 else 20).put("important", important)
            .put("attempts", 0).put("state", "pending"))
        vault.put("signal_queue", records.toString())
        return@synchronized id
    }
    fun next(): SignalItem? = synchronized(processLock) {
        val array = records()
        val pending = (0 until array.length()).map { array.getJSONObject(it) }
            .filter { it.optString("state") == "pending" }
            .sortedWith(compareByDescending<JSONObject> { it.optInt("priority") }.thenBy { it.optLong("created") })
        val row = pending.firstOrNull() ?: return@synchronized null
        return@synchronized SignalItem(row.getString("id"), row.getString("source"), row.getString("title"),
            row.getLong("created"), row.getInt("priority"), row.getBoolean("important"),
            row.getInt("attempts"))
    }
    fun finish(id: String, success: Boolean): Boolean = synchronized(processLock) {
        val array = records()
        for (i in 0 until array.length()) {
            val row = array.getJSONObject(i)
            if (row.optString("id") != id) continue
            if (row.optString("state") == "done") return@synchronized true
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
            return@synchronized true
        }
        return@synchronized false
    }
    fun pendingCount(): Int = synchronized(processLock) {
        val items = records()
        (0 until items.length()).count { i -> items.getJSONObject(i).optString("state") == "pending" }
    }
    private fun records(): JSONArray = JSONArray(vault.get("signal_queue") ?: "[]")
}
