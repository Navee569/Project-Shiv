package com.shiv.assistant.integrations

import com.shiv.assistant.data.EventRecord
import org.junit.Assert.*
import org.junit.Test

class CalendarExportPolicyTest {
    @Test fun exportsOnlySafeLocalFutureEvents() {
        val now=1_800_000_000_000L
        val event=EventRecord("id","Dentist visit",now+60_000,now+3_600_000,"owner")
        assertTrue(CalendarExportPolicy.approved(event,now))
        assertFalse(CalendarExportPolicy.approved(event.copy(source="owner-reviewed-calendar"),now))
        assertFalse(CalendarExportPolicy.approved(event.copy(title="OTP 123456"),now))
        assertFalse(CalendarExportPolicy.approved(event.copy(startAt=now-1),now))
        assertFalse(CalendarExportPolicy.approved(event.copy(endAt=now),now))
    }
}
