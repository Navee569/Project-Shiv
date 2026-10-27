package com.shiv.assistant.ai

import org.junit.Assert.*
import org.junit.Test

class LocalDiagnosticsTest {
    @Test fun invalidRuntimeMeasurementsRemainUnavailable() {
        assertNull(LocalDiagnostics.positiveFinite(Double.NaN))
        assertNull(LocalDiagnostics.positiveFinite(Double.POSITIVE_INFINITY))
        assertNull(LocalDiagnostics.positiveFinite(0.0))
        assertEquals(14.2,LocalDiagnostics.positiveFinite(14.2)!!,0.0)
        assertNull(LocalDiagnostics.tokenCount(0))
        assertEquals(42,LocalDiagnostics.tokenCount(42))
    }
    @Test fun batteryCounterIsOnlyAWholeDeviceDischargeObservation() {
        assertEquals(200L,LocalDiagnostics.deviceEnergyDropNwh(1200,1000,true))
        assertNull(LocalDiagnostics.deviceEnergyDropNwh(1200,1000,false))
        assertNull(LocalDiagnostics.deviceEnergyDropNwh(1000,1200,true))
        assertNull(LocalDiagnostics.deviceEnergyDropNwh(Long.MIN_VALUE,1000,true))
    }
}
