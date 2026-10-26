package com.shiv.assistant.vision

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class OcrTaskParserTest {
    private val today = LocalDate.of(2026,10,26)
    @Test fun onlyExplicitUnambiguousDatesProduceProposals() {
        assertEquals(today.plusDays(5), OcrTaskParser.parse(
            "Invoice due date: 2026-10-31", today)?.dueDate)
        assertNull(OcrTaskParser.parse("Pay by 31/10/26", today))
        assertNull(OcrTaskParser.parse("2026-10-31 posted", today))
        assertNull(OcrTaskParser.parse("Due 2026-02-31", today))
        assertNull(OcrTaskParser.parse("due 2024-10-31", today))
        assertNull(OcrTaskParser.parse("due 2029-10-31", today))
    }
}
