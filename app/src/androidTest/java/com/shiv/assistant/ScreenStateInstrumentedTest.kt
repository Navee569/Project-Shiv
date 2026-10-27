package com.shiv.assistant

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenStateInstrumentedTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Before fun emptyOwnerDrafts() {
        compose.runOnIdle {
            val controller=ViewModelProvider(compose.activity)[ShivViewModel::class.java].controller
            controller.messages.clear()
            controller.notes.clear()
            controller.macros.clear()
        }
    }

    @Test fun navigationAndEmptyChatSurviveActivityRecreation() {
        compose.onNodeWithContentDescription("Open Chat screen").performClick()
        compose.onNodeWithText("No conversation yet. Type a message or use offline voice.")
            .assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("No conversation yet. Type a message or use offline voice.")
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("Open Chat screen").assertHasClickAction()
    }

    @Test fun mainNavigationExposesAccessibleTabsAndEmptyStates() {
        compose.onNodeWithContentDescription("Open Automate screen").performClick()
        compose.onNodeWithText("No saved macros yet.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open Memory screen").performClick()
        compose.onNodeWithText("No saved notes yet.").assertIsDisplayed()
    }

    @Test fun everyPrimaryScreenIsReachableWithSelectedTabSemantics() {
        for(name in listOf("Core","Chat","Automate","Memory","Memories","Models",
            "AI Status","Events","Beliefs","Decisions","Permissions","Settings")) {
            compose.onNodeWithContentDescription("Open $name screen")
                .performScrollTo().performClick().assertIsSelected()
        }
    }

    @Test fun permissionDenialIsAnnouncedOnPermissionsScreen() {
        compose.onNodeWithContentDescription("Open Permissions screen")
            .performScrollTo().performClick()
        compose.runOnIdle {
            ViewModelProvider(compose.activity)[ShivViewModel::class.java]
                .controller.phase.reportIssue("Calendar permission denied; no phone events were read")
        }
        compose.onNodeWithText("Calendar permission denied; no phone events were read")
            .assertIsDisplayed()
    }
}
