package com.shiv.assistant.domain

/** Deterministic candidates only, never confirmed owner commitments or model-generated claims. */
data class ExtractedSignalFact(val summary: String, val confidence: Double, val evidence: String)
object SignalFacts {
    private val eventWords = Regex("(?i)\\b(meeting|appointment|deadline|due|visit|interview|reminder)\\b|ಸಭೆ|ನಾಳೆ|సమావేశం|రేపు")
    private val timeWords = Regex("(?i)\\b(today|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday|" +
        "am|pm|at\\s+\\d{1,2}|\\d{1,2}:\\d{2})\\b|ಇಂದು|ನಾಳೆ|ఈరోజు|రేపు")
    fun extract(notificationTitle: String): ExtractedSignalFact? {
        val raw = notificationTitle.trim()
        if (raw.length !in 5..200) return null
        val event = eventWords.find(raw) ?: return null
        val time = timeWords.find(raw) ?: return null
        // Preserve a short contextual fact without treating an unknown date as certain.
        val summary = "Possible event: " + raw.take(120)
        return ExtractedSignalFact(summary, 0.55, "Event term '" + event.value +
            "' and time term '" + time.value + "' in owner-permitted notification title")
    }
}
