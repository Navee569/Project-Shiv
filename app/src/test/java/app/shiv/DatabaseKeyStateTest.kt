package app.shiv

import org.junit.Assert.assertEquals
import org.junit.Test

class DatabaseKeyStateTest {
    @Test fun firstRunWithoutMetadataAndDatabaseMayCreateKey() {
        assertEquals(DatabaseKeyState.FIRST_RUN, databaseKeyState(false, false))
    }

    @Test fun missingMetadataWithExistingDatabaseRequiresRecovery() {
        assertEquals(DatabaseKeyState.MISSING_METADATA_WITH_DATABASE, databaseKeyState(false, true))
    }

    @Test fun existingMetadataUsesWrappedKeyPathRegardlessOfDatabaseFile() {
        assertEquals(null, databaseKeyState(true, false))
        assertEquals(null, databaseKeyState(true, true))
    }
}
