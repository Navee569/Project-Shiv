package app.shiv.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.CompositionLocalProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import app.shiv.domain.model.UserProfile
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ShivNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun actualShellRetainsChatAndProfileDraftsAcrossNavigationAndSavedStateRestore() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent { StatefulShellContent() }

        composeRule.onNodeWithText("Chat draft").performTextInput("unsent message")
        composeRule.onNodeWithText("Memory").performClick()
        composeRule.onNodeWithText("Profile name").performTextInput("Ada")
        composeRule.onNodeWithText("Profile notes").performTextInput("likes quiet mornings")

        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText("Ada").assertExists()
        composeRule.onNodeWithText("likes quiet mornings").assertExists()
        composeRule.onNodeWithText("Chat").performClick()
        composeRule.onNodeWithText("unsent message").assertExists()
    }

    @Test
    fun lateProfileLoadPreservesEditsAndFillsOnlyUntouchedField() {
        val loaded = mutableStateOf<UserProfile?>(null)
        composeRule.setContent {
            MaterialTheme {
                val state = rememberShivShellState()
                LaunchedEffect(loaded.value) { state.hydrateProfile(loaded.value) }
                ShivShell(state) { _, drafts ->
                    Column {
                        OutlinedTextField(drafts.profileNameDraft.value, drafts::editProfileName, label = { Text("Profile name") })
                        OutlinedTextField(drafts.profileNotesDraft.value, drafts::editProfileNotes, label = { Text("Profile notes") })
                    }
                }
            }
        }

        composeRule.onNodeWithText("Profile name").performTextInput("New name")
        composeRule.runOnIdle { loaded.value = UserProfile(name = "Old name", notes = "Saved notes") }
        composeRule.onNodeWithText("New name").assertExists()
        composeRule.onNodeWithText("Saved notes").assertExists()
        composeRule.onNodeWithText("Old name").assertDoesNotExist()
    }

    @Test
    fun tierBadgeIsDescriptiveAndHasNoClickAction() {
        composeRule.setContent { MaterialTheme { TierChip("ASK_FIRST") } }
        composeRule.onNodeWithTag("tier-badge-ASK_FIRST").assertHasNoClickAction()
        composeRule.onNodeWithText("ASK_FIRST").assertExists()
    }

    @Test
    fun actualShellSettingsDestinationHandlesBackNavigation() {
        val activity = AtomicReference<ComponentActivity?>()
        composeRule.setContent {
            val context = LocalContext.current
            SideEffect { activity.set(context.findActivity()) }
            TestShivShell { destination, _ -> Text("Screen: ${destination.title}") }
        }

        composeRule.onNodeWithContentDescription("Events", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Open Local AI models").performClick()
        composeRule.onNodeWithText("Screen: Local AI models").assertExists()
        composeRule.runOnIdle { activity.get()?.onBackPressedDispatcher?.onBackPressed() }
        composeRule.onNodeWithText("Settings screen").assertExists()
    }

    @Test
    fun notificationCapabilityHonorsAppToggleBeforeRuntimePermissionApis() {
        assertFalse(notificationCapabilityGranted(apiLevel = 28, appNotificationsEnabled = false, postNotificationsGranted = true))
        assertTrue(notificationCapabilityGranted(apiLevel = 28, appNotificationsEnabled = true, postNotificationsGranted = false))
        assertFalse(notificationCapabilityGranted(apiLevel = 33, appNotificationsEnabled = true, postNotificationsGranted = false))
        assertTrue(notificationCapabilityGranted(apiLevel = 33, appNotificationsEnabled = true, postNotificationsGranted = true))
    }

    @Test
    fun shellShowsHonestEmptyLoadingErrorAndUnavailableTaskStates() {
        val inventory = mutableStateOf(InventoryTestState(loading = true))
        composeRule.setContent {
            MaterialTheme {
                Column {
                    ChatEmptyPrompt()
                    MemoryEmptyNotice()
                    TasksUnavailableNotice()
                    ModelInventoryNotice(
                        loading = inventory.value.loading,
                        error = inventory.value.error,
                        fastInstalled = false,
                        chatInstalled = false,
                        detailedEmptyText = true
                    )
                }
            }
        }
        composeRule.onNodeWithText("Hey, I’m Shiv.").assertExists()
        composeRule.onNodeWithText("Tell Shiv ‘remember that…’ or share a preference to build your private memory.").assertExists()
        composeRule.onNodeWithText("Task tracking is not available yet. Shiv does not create or track tasks.").assertExists()
        composeRule.onNodeWithText("Checking private model storage…").assertExists()

        composeRule.runOnIdle { inventory.value = InventoryTestState(error = "Model storage could not be read.") }
        composeRule.onNodeWithText("Model storage could not be read.").assertExists()
        composeRule.runOnIdle { inventory.value = InventoryTestState() }
        composeRule.onNodeWithText("No models are installed. Chat answers that require a language model will be unavailable until you import one.").assertExists()
    }

    @Test
    fun permissionExplanationRefreshesAfterReturningToResumedLifecycle() {
        val owner = TestLifecycleOwner()
        val notificationsEnabled = AtomicBoolean(true)
        val status = PermissionExplanationStatus(true, true, true, true, true, true)
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    PermissionExplanationTab(
                        modifier = Modifier.fillMaxSize(),
                        statusProvider = { status.copy(notifications = notificationsEnabled.get()) }
                    )
                }
            }
        }
        composeRule.onNodeWithTag("permission-notifications-status").assertTextEquals("Allowed")

        composeRule.runOnIdle {
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            notificationsEnabled.set(false)
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        composeRule.onNodeWithTag("permission-notifications-status").assertTextEquals("Not allowed")
    }
}

@Composable
private fun StatefulShellContent() = TestShivShell { destination, state ->
    when (destination) {
        ShivDestination.CHAT -> OutlinedTextField(
            value = state.chatDraft.value,
            onValueChange = { state.chatDraft.value = it },
            label = { Text("Chat draft") }
        )
        ShivDestination.MEMORY -> Column {
            OutlinedTextField(
                value = state.profileNameDraft.value,
                onValueChange = { state.profileNameDraft.value = it },
                label = { Text("Profile name") }
            )
            OutlinedTextField(
                value = state.profileNotesDraft.value,
                onValueChange = { state.profileNotesDraft.value = it },
                label = { Text("Profile notes") }
            )
        }
        else -> Text("Screen: ${destination.title}")
    }
}

@Composable
private fun TestShivShell(content: @Composable (ShivDestination, ShivShellState) -> Unit) {
    val state = rememberShivShellState()
    ShivShell(state = state) { destination, shellState ->
        if (destination == ShivDestination.SETTINGS) {
            Column {
                Text("Settings screen")
                Button(onClick = { shellState.destination.value = ShivDestination.MODELS }) { Text("Open Local AI models") }
            }
        } else {
            content(destination, shellState)
        }
    }
}

private class TestLifecycleOwner : LifecycleOwner {
    val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
}

private data class InventoryTestState(val loading: Boolean = false, val error: String? = null)

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
