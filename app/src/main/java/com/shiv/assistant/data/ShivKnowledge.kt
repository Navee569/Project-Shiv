package com.shiv.assistant.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class MemoryLayer { WORKING, EPISODIC, SEMANTIC, PROCEDURAL }
data class MemoryFact(
    val id: String,
    val layer: MemoryLayer,
    val text: String,
    val source: String,
    val createdAt: Long,
    val confidence: Double,
    val explicit: Boolean,
    val supersedes: String?
)
/** Evidence-bearing encrypted local memory, separate from legacy plain note strings. */
class ShivKnowledge(private val vault: ShivVault) {
    @Synchronized
    fun save(
        text: String, layer: MemoryLayer = MemoryLayer.SEMANTIC,
        source: String = "owner", explicit: Boolean = true,
        confidence: Double = if (explicit) 1.0 else 0.5
    ): MemoryFact {
        require(text.trim().length in 1..2048)
        require(confidence in 0.0..1.0)
        val old = all()
        old.firstOrNull { it.text.equals(text.trim(), true) && it.layer == layer && it.explicit == explicit }
            ?.let { return it }
        val record = MemoryFact(UUID.randomUUID().toString(), layer, text.trim(),
            source.take(120), System.currentTimeMillis(), confidence, explicit, null)
        store(old + record)
        return record
    }
    @Synchronized
    fun correct(id: String, newText: String): MemoryFact? {
        val old = all()
        val fact = old.find { it.id == id } ?: return null
        require(newText.trim().length in 1..2048)
        val changed = MemoryFact(UUID.randomUUID().toString(), fact.layer, newText.trim(),
            "owner correction", System.currentTimeMillis(), 1.0, true, id)
        store(old + changed)
        return changed
    }
    @Synchronized
    fun remove(id: String) { store(all().filterNot { it.id == id }) }
    fun all(): List<MemoryFact> {
        val array = JSONArray(vault.get("structured_memory") ?: "[]")
        return (0 until array.length()).map { i ->
            val value = array.getJSONObject(i)
            MemoryFact(value.getString("id"), MemoryLayer.valueOf(value.getString("layer")),
                value.getString("text"), value.getString("source"), value.getLong("created"),
                value.getDouble("confidence"), value.getBoolean("explicit"),
                value.optString("supersedes").takeIf { it.isNotBlank() })
        }
    }
    /** Never treat an inference as overriding a corrected owner fact. */
    fun active(): List<MemoryFact> {
        val entries = all()
        val superseded = entries.mapNotNull { it.supersedes }.toSet()
        return entries.filterNot { it.id in superseded }
    }
    fun search(query: String): List<MemoryFact> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.length > 2 }
        return active().map { it to words.count { w -> w in it.text.lowercase() } }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<MemoryFact, Int>> { it.second }
                .thenByDescending { it.first.explicit }.thenByDescending { it.first.createdAt })
            .take(12).map { it.first }
    }
    @Synchronized
    fun consolidate(): Int {
        val old = all()
        val seen = mutableSetOf<String>()
        val kept = old.filter { item ->
            // Never drop a correction chain or an explicit owner fact.
            if (item.explicit) true else seen.add(item.layer.name + "|" + item.text.lowercase())
        }
        if (kept.size != old.size) store(kept)
        return old.size - kept.size
    }
    private fun store(items: List<MemoryFact>) {
        val array = JSONArray()
        items.forEach { f ->
            array.put(JSONObject().put("id", f.id).put("layer", f.layer.name)
                .put("text", f.text).put("source", f.source)
                .put("created", f.createdAt).put("confidence", f.confidence)
                .put("explicit", f.explicit).put("supersedes", f.supersedes ?: ""))
        }
        vault.put("structured_memory", array.toString())
    }
}
