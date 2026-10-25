package com.shiv.assistant

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityLifecycleTest {
    @Test fun launchWithoutModelsOrPermissionsAndRecreate() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse("Shiv must stay open without cloud/local weights",activity.isFinishing)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertFalse("Shiv must survive recreation",activity.isFinishing)
            }
        }
    }
}
