package com.shiv.assistant.ai

import org.junit.Assert.*
import org.junit.Test

class LocalInferenceSettingsTest {
    @Test fun malformedSettingsFailToConservativeDefaults() {
        assertEquals(LocalBackend.CPU,LocalInferenceSettings.backend(null))
        assertEquals(LocalBackend.CPU,LocalInferenceSettings.backend("ROOT"))
        assertEquals(384,LocalInferenceSettings.outputTokens(null))
        assertEquals(384,LocalInferenceSettings.outputTokens("999999"))
        assertEquals(128,LocalInferenceSettings.outputTokens("128"))
    }
}
