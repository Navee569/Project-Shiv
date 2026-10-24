package app.shiv.domain.memory

/** Deterministic, local-only matching used to select a memory without invoking a model. */
object MemoryRelevance {
    private val stopWords = setOf(
        "that", "this", "with", "from", "when", "what", "have", "your", "about",
        "would", "could", "please", "remind", "meeting", "today", "tomorrow"
    )

    fun matches(input: String, fact: String): Boolean {
        val words = input.lowercase()
            .split(Regex("[^\\p{L}0-9]+"))
            .filter { it.length > 3 && it !in stopWords }
        val normalizedFact = fact.lowercase()
        return words.any { it in normalizedFact }
    }
}
