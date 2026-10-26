package com.shiv.assistant.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.UUID

enum class AgendaKind { EVENT, TASK }
data class AgendaItem(
    val id: String,
    val title: String,
    val dueEpochMs: Long,
    val kind: AgendaKind,
    val completed: Boolean,
    val source: String
)

/** Deterministic date parsing and local encrypted agenda; no guessed event creation. */
object AgendaDates {
    private val format = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")
        .withResolverStyle(ResolverStyle.STRICT)
    fun parseLocal(text: String, zone: ZoneId = ZoneId.systemDefault()): Long? {
        return try {
            LocalDateTime.parse(text.trim(), format).atZone(zone).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) { null }
    }
    fun formatLocal(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(format)
    fun isOnDay(millis: Long, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()) =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate() == day
}

class ShivAgenda(private val vault: ShivVault) {
    @Synchronized
    fun add(title: String, whenLocal: String, kind: AgendaKind): AgendaItem {
        val clean = title.trim()
        require(clean.length in 1..300) { "Event title must be 1-300 characters" }
        val millis = AgendaDates.parseLocal(whenLocal)
            ?: throw IllegalArgumentException("Enter a valid date as YYYY-MM-DD HH:mm")
        require(millis > System.currentTimeMillis() - 60_000L) { "Date must be in the future" }
        val item = AgendaItem(UUID.randomUUID().toString(), clean, millis, kind, false, "owner")
        save(list() + item)
        return item
    }
    /**
     * Called only after the owner confirms previewing an Android calendar instance.
     * Importing the same event twice cannot create duplicates.
     */
    @Synchronized
    fun importCalendar(eventId: Long, title: String, startMillis: Long): Boolean {
        val safeTitle = title.trim()
        require(eventId > 0 && safeTitle.length in 1..256)
        require(startMillis > System.currentTimeMillis() - 60000L)
        require(startMillis < System.currentTimeMillis() + 365L * 86400000L)
        val provenance = "calendar:" + eventId
        val records = list()
        if (records.any { it.source == provenance && it.dueEpochMs == startMillis }) return false
        require(records.size < 1500) { "Agenda capacity reached" }
        save(records + AgendaItem(UUID.randomUUID().toString(), safeTitle,
            startMillis, AgendaKind.EVENT, false, provenance))
        return true
    }
    @Synchronized
    fun rename(id: String, newTitle: String) {
        val title = newTitle.trim()
        require(title.length in 1..300)
        val existing = list()
        require(existing.any { it.id == id }) { "Unknown event id" }
        save(existing.map { if (it.id == id) it.copy(title = title) else it })
    }
    @Synchronized
    fun markCompleted(id: String, completed: Boolean) {
        save(list().map { if (it.id == id) it.copy(completed = completed) else it })
    }
    @Synchronized
    fun remove(id: String) { save(list().filterNot { it.id == id }) }
    fun list(): List<AgendaItem> {
        val data = JSONArray(vault.get("agenda") ?: "[]")
        return (0 until data.length()).map { index ->
            val row = data.getJSONObject(index)
            AgendaItem(row.getString("id"), row.getString("title"), row.getLong("due"),
                AgendaKind.valueOf(row.getString("kind")), row.getBoolean("completed"),
                row.optString("source", "owner"))
        }.sortedWith(compareBy<AgendaItem> { it.dueEpochMs }.thenBy { it.title })
    }
    fun onDay(day: LocalDate): List<AgendaItem> =
        list().filter { AgendaDates.isOnDay(it.dueEpochMs, day) }
    fun upcoming(hours: Int = 48): List<AgendaItem> {
        require(hours in 1..168)
        val from = System.currentTimeMillis()
        return list().filter { !it.completed && it.dueEpochMs >= from &&
            it.dueEpochMs <= from + hours * 3_600_000L }
    }
    private fun save(all: List<AgendaItem>) {
        require(all.size <= 1500) { "Agenda capacity reached" }
        val json = JSONArray()
        all.forEach { value ->
            json.put(JSONObject().put("id", value.id).put("title", value.title)
                .put("due", value.dueEpochMs).put("kind", value.kind.name)
                .put("completed", value.completed).put("source", value.source))
        }
        vault.put("agenda", json.toString())
    }
}
