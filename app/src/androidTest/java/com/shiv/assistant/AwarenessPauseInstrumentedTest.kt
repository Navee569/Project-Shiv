package com.shiv.assistant

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shiv.assistant.awareness.AwarenessControl
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AwarenessPauseInstrumentedTest {
    @Test fun pauseBlocksCaptureAndResumeNeverRestoresScreenToken() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("shiv_awareness_control",Context.MODE_PRIVATE)
        val previousPaused=prefs.getBoolean("paused",false)
        val previousToken=prefs.getLong("capture_until",0L)
        val previousNotifications=prefs.getBoolean("notifications_enabled",false)
        val previousOwner=prefs.getBoolean("owner_enabled",false)
        try {
            assertTrue(prefs.edit().putBoolean("notifications_enabled",false)
                .putBoolean("owner_enabled",false)
                .putBoolean("paused",false).putLong("capture_until",12345L).commit())
            AwarenessControl.pause(context)
            assertTrue(AwarenessControl.isPaused(context))
            assertFalse(AwarenessControl.mayCapture(context))
            assertEquals(0L,prefs.getLong("capture_until",0L))
            AwarenessControl.resume(context)
            assertFalse(AwarenessControl.isPaused(context))
            assertEquals(0L,prefs.getLong("capture_until",0L))
        } finally {
            prefs.edit().putBoolean("paused",previousPaused)
                .putLong("capture_until",previousToken)
                .putBoolean("notifications_enabled",previousNotifications)
                .putBoolean("owner_enabled",previousOwner).commit()
            AwarenessControl.show(context)
        }
    }
}
