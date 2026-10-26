package com.shiv.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class SpeechChunkerTest {
    @Test fun sentencesCanArriveAcrossArbitraryModelFragments() {
        val chunks=SpeechChunker()
        assertTrue(chunks.accept("Meet Raj at").isEmpty())
        assertEquals(listOf("Meet Raj at five."),chunks.accept(" five. Bring notes"))
        assertEquals(listOf("Bring notes?"),chunks.accept("? Then leave"))
        assertEquals("Then leave",chunks.finish())
        assertEquals("",chunks.finish())
    }
    @Test fun unpunctuatedOutputIsBounded() {
        val chunks=SpeechChunker(40)
        val ready=chunks.accept("This is a long answer that continues without punctuation and more words")
        assertTrue(ready.isNotEmpty())
        assertTrue(ready.all { it.length<=40 })
    }
}
