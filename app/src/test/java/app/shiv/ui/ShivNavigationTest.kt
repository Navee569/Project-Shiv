package app.shiv.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ShivNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun navigationSelectionRecomposesAndSurvivesSavedStateRestore() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent { NavigationStateProbe() }

        composeRule.onNodeWithText("Open models").performClick()
        composeRule.onNodeWithText("Local AI models").assertExists()
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText("Local AI models").assertExists()
    }

    @Test
    fun unknownSavedDestinationFallsBackToChat() {
        assertEquals(ShivDestination.CHAT, ShivDestination.fromSavedName("REMOVED_SCREEN"))
        assertEquals(ShivDestination.CHAT, ShivDestination.fromSavedName(null))
    }
}

@Composable
private fun NavigationStateProbe() {
    val destination = rememberShivDestination()
    MaterialTheme {
        Column {
            Text(destination.value.title)
            Button(onClick = { destination.value = ShivDestination.MODELS }) { Text("Open models") }
        }
    }
}
