package app.shiv

import android.content.Context
import androidx.work.WorkerParameters

/** Compatibility component names retained for requests persisted before the UI/platform package split. */
class MainActivity : app.shiv.ui.MainActivity()
class ListeningService : app.shiv.platform.ListeningService()
class ReminderVoiceService : app.shiv.platform.ReminderVoiceService()
class CalendarNotificationListener : app.shiv.platform.CalendarNotificationListener()
class ReminderReceiver : app.shiv.platform.ReminderReceiver()
class PendingEventReceiver : app.shiv.platform.PendingEventReceiver()
class BootReceiver : app.shiv.platform.BootReceiver()
class ProactiveWorker(context: Context, params: WorkerParameters) : app.shiv.platform.ProactiveWorker(context, params)
class ReminderWorker(context: Context, params: WorkerParameters) : app.shiv.platform.ReminderWorker(context, params)
