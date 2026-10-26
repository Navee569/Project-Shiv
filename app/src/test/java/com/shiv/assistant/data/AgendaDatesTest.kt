package com.shiv.assistant.data

import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate
import java.time.ZoneId

class AgendaDatesTest {
    @Test fun parsesLocalTimeWithoutInventingZone() {
        val zone = ZoneId.of("Asia/Kolkata")
        val time = AgendaDates.parseLocal("2027-02-14 17:30", zone)
        assertNotNull(time)
        assertEquals("2027-02-14 17:30", AgendaDates.formatLocal(time!!, zone))
        assertTrue(AgendaDates.isOnDay(time, LocalDate.of(2027, 2, 14), zone))
        assertFalse(AgendaDates.isOnDay(time, LocalDate.of(2027, 2, 15), zone))
    }
    @Test fun rejectsAmbiguousMalformedDates() {
        assertNull(AgendaDates.parseLocal("tomorrow at five"))
        assertNull(AgendaDates.parseLocal("2027-02-31 08:00"))
        assertNull(AgendaDates.parseLocal("2027-02-14"))
    }
}
