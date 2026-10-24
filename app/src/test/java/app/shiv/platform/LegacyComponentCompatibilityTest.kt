package app.shiv.platform

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import app.shiv.MainActivity
import app.shiv.BootReceiver
import app.shiv.CalendarNotificationListener
import app.shiv.ListeningService
import app.shiv.PendingEventReceiver
import app.shiv.ProactiveWorker as LegacyProactiveWorker
import app.shiv.ReminderReceiver
import app.shiv.ReminderVoiceService
import app.shiv.ReminderWorker as LegacyReminderWorker
import androidx.work.WorkerParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LegacyComponentCompatibilityTest {
    @Test
    @Suppress("DEPRECATION")
    fun oldManifestComponentNamesRemainRegistered() {
        val context = RuntimeEnvironment.getApplication()
        val packageManager = context.packageManager
        packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        packageManager.getServiceInfo(ComponentName(context, ListeningService::class.java), 0)
        packageManager.getServiceInfo(ComponentName(context, ReminderVoiceService::class.java), 0)
        packageManager.getServiceInfo(ComponentName(context, CalendarNotificationListener::class.java), 0)
        packageManager.getReceiverInfo(ComponentName(context, ReminderReceiver::class.java), 0)
        packageManager.getReceiverInfo(ComponentName(context, PendingEventReceiver::class.java), 0)
        packageManager.getReceiverInfo(ComponentName(context, BootReceiver::class.java), 0)
    }

    @Test
    fun legacyWorkManagerClassNameResolvesToCurrentWorkerImplementation() {
        assertEquals(ProactiveWorker::class.java, LegacyProactiveWorker::class.java.superclass)
        assertNotNull(LegacyProactiveWorker::class.java.getConstructor(Context::class.java, WorkerParameters::class.java))
        assertEquals(ReminderWorker::class.java, LegacyReminderWorker::class.java.superclass)
        assertNotNull(LegacyReminderWorker::class.java.getConstructor(Context::class.java, WorkerParameters::class.java))
    }
}
