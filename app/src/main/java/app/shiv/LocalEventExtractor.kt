package app.shiv

import java.util.Calendar

/** Small offline beta extractor. It requires a reminder cue and a relative day phrase. */
object LocalEventExtractor {
    private val reminderCue = Regex("\\b(remind me|remember to|don't forget to)\\b", RegexOption.IGNORE_CASE)
    fun extract(text: String): MemoryEvent? {
        if (!reminderCue.containsMatchIn(text)) return null
        val dayMatch = Regex("\\bin (\\d+) days?\\b", RegexOption.IGNORE_CASE).find(text)
        val days = when {
            Regex("\\btomorrow\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> 1
            dayMatch != null -> dayMatch.groupValues[1].toIntOrNull() ?: return null
            else -> return null
        }
        val title = text.replace(reminderCue, "").replace(Regex("\\btomorrow\\b|\\bin \\d+ days?\\b", RegexOption.IGNORE_CASE), "").trim().trim('.', '!', ',')
        if (title.isBlank()) return null
        val at = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, days); set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        return MemoryEvent(kind = "REMINDER", title = title, reminderAt = at)
    }
}
