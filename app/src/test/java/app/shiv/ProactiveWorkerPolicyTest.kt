package app.shiv

import app.shiv.platform.proactiveNotificationTier
import app.shiv.domain.actions.ActionTier
import app.shiv.domain.scheduling.ProactiveSchedulePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProactiveWorkerPolicyTest {
    @Test
    fun quietHoursCrossMidnight() {
        assertTrue(ProactiveSchedulePolicy.isQuietMinute(22 * 60, 22 * 60, 8 * 60))
        assertTrue(ProactiveSchedulePolicy.isQuietMinute(7 * 60 + 59, 22 * 60, 8 * 60))
        assertFalse(ProactiveSchedulePolicy.isQuietMinute(8 * 60, 22 * 60, 8 * 60))
        assertFalse(ProactiveSchedulePolicy.isQuietMinute(12 * 60, 22 * 60, 8 * 60))
    }

    @Test
    fun equalQuietHourBoundsMeanAllDayQuiet() {
        assertTrue(ProactiveSchedulePolicy.isQuietMinute(12 * 60, 8 * 60, 8 * 60))
    }

    @Test
    fun eligibilityRejectsDisabledZeroCapAndQuietTime() {
        assertFalse(ProactiveSchedulePolicy.isEligible(false, 3, 12 * 60, 22 * 60, 8 * 60))
        assertFalse(ProactiveSchedulePolicy.isEligible(true, 0, 12 * 60, 22 * 60, 8 * 60))
        assertFalse(ProactiveSchedulePolicy.isEligible(true, 3, 23 * 60, 22 * 60, 8 * 60))
        assertTrue(ProactiveSchedulePolicy.isEligible(true, 3, 12 * 60, 22 * 60, 8 * 60))
    }

    @Test
    fun notificationTierCannotLowerMinimumAndAskFirstIsDeniedInBackground() {
        assertEquals(ActionTier.ACT_THEN_TELL, proactiveNotificationTier(ActionTier.FREE.name))
        assertEquals(ActionTier.ACT_THEN_TELL, proactiveNotificationTier(ActionTier.ACT_THEN_TELL.name))
        assertEquals(null, proactiveNotificationTier(ActionTier.ASK_FIRST.name))
    }
}
