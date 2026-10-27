package com.shiv.assistant

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shiv.assistant.ui.StorageRecoveryScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageRecoveryScreenInstrumentedTest {
    @get:Rule val compose=createComposeRule()

    @Test fun eraseIsUnavailableUntilOwnerTypesExactPhrase() {
        var erased=false
        compose.setContent { StorageRecoveryScreen("Storage unavailable",{}, { erased=true }) }
        compose.onNodeWithText("ERASE AND START FRESH").assertIsNotEnabled()
        compose.onNodeWithText("Type ERASE SHIV STORAGE").performTextInput("ERASE SHIV STORAGE")
        Espresso.pressBack()
        compose.onNodeWithText("ERASE AND START FRESH").assertIsEnabled().performScrollTo().performClick()
        compose.runOnIdle { assertTrue(erased) }
    }
}
