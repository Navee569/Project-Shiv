package app.shiv

import app.shiv.domain.memory.MemoryRelevance
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRelevanceTest {
    @Test
    fun matchesMeaningfulWordsCaseInsensitively() {
        assertTrue(MemoryRelevance.matches("What snacks do I prefer?", "Prefers roasted almonds"))
    }

    @Test
    fun ignoresPunctuationAndStopWords() {
        assertTrue(MemoryRelevance.matches("Please remind me about Kannada", "Learning Kannada"))
        assertFalse(MemoryRelevance.matches("What about this?", "Prefers roasted almonds"))
    }

    @Test
    fun doesNotMatchShortTokens() {
        assertFalse(MemoryRelevance.matches("I enjoy tea", "Likes coffee"))
    }
}
