package app.shiv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

internal enum class ShivDestination(val title: String, val isPrimary: Boolean = false) {
    CHAT("Chat", true),
    EVENTS("Events", true),
    MEMORY("Memory", true),
    SETTINGS("Settings", true),
    MODELS("Local AI models"),
    AI_STATUS("AI status"),
    PERMISSIONS("Permission explanations");

    companion object {
        fun fromSavedName(name: String?): ShivDestination =
            entries.firstOrNull { it.name == name } ?: CHAT
    }
}

private val destinationSaver = Saver<ShivDestination, String>(
    save = { it.name },
    restore = ShivDestination::fromSavedName
)

@Composable
internal fun rememberShivDestination(): MutableState<ShivDestination> =
    rememberSaveable(stateSaver = destinationSaver) { mutableStateOf(ShivDestination.CHAT) }
