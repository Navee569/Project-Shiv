package app.shiv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProactiveWorkerPolicyTest {
    @Test
    fun quietHoursCrossMidnight() {
        assertTrue(isQuietMinute(22 * 60, 22 * 60, 8 * 60))
        assertTrue(isQuietMinute(7 * 60 + 59, 22 * 60, 8 * 60))
        assertFalse(isQuietMinute(8 * 60, 22 * 60, 8 * 60))
        assertFalse(isQuietMinute(12 * 60, 22 * 60, 8 * 60))
    }

    @Test
    fun equalQuietHourBoundsMeanAllDayQuiet() {
        assertTrue(isQuietMinute(12 * 60, 8 * 60, 8 * 60))
    }

    @Test
    fun notificationTierCannotLowerMinimumAndAskFirstIsDeniedInBackground() {
        assertEquals(ActionTier.ACT_THEN_TELL, proactiveNotificationTier(ActionTier.FREE.name))
        assertEquals(ActionTier.ACT_THEN_TELL, proactiveNotificationTier(ActionTier.ACT_THEN_TELL.name))
        assertEquals(null, proactiveNotificationTier(ActionTier.ASK_FIRST.name))
    }
}
