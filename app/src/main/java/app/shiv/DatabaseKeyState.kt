package app.shiv

/** Distinguishes first-run key creation from missing metadata for an existing encrypted database. */
internal enum class DatabaseKeyState { FIRST_RUN, MISSING_METADATA_WITH_DATABASE }

internal fun databaseKeyState(hasWrappedMetadata: Boolean, databaseFileExists: Boolean): DatabaseKeyState? =
    when {
        hasWrappedMetadata -> null
        databaseFileExists -> DatabaseKeyState.MISSING_METADATA_WITH_DATABASE
        else -> DatabaseKeyState.FIRST_RUN
    }
