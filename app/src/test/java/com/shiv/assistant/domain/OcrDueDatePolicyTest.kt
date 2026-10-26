package com.shiv.assistant.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class OcrDueDatePolicyTest {
    private val today=LocalDate.of(2026,10,26)
    @Test fun explicitDueDateParsesButAmbiguousOrImpossibleDateDoesNot() {
        assertEquals(LocalDate.of(2026,11,30),
            OcrDueDatePolicy.propose("Invoice DUE DATE: 2026-11-30",today)?.date)
        assertNull(OcrDueDatePolicy.propose("Pay by 30/11/26",today))
        assertNull(OcrDueDatePolicy.propose("printed 2026-11-30",today))
        assertNull(OcrDueDatePolicy.propose("due 2026-02-30",today))
        assertNull(OcrDueDatePolicy.propose("deadline 2027-12-05",today))
        assertNull(OcrDueDatePolicy.propose("due 2024-11-20",today))
    }
}
