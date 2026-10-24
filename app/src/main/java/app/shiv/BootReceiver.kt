package app.shiv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.shiv.data.mapping.RoomRecordMappers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val dao = ShivDatabase.get(context).dao()
                val now = System.currentTimeMillis()
                val lead = dao.reminderRule(Brain.REMINDER_LEAD_KEY)?.value?.toIntOrNull() ?: Brain.DEFAULT_LEAD_MINUTES
                dao.upcomingEvents(now).forEach { AlarmScheduler.schedule(context, RoomRecordMappers.toDomain(it), lead) }
            } finally { pending.finish() }
        }
    }
}
