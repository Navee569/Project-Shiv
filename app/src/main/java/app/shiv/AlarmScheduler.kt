package app.shiv

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object AlarmScheduler {
    const val EXTRA_EVENT_ID = "event_id"
    const val EXTRA_TITLE = "event_title"
    const val EXTRA_START = "event_start"

    fun schedule(context: Context, event: EventRecord, leadMinutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val fireAt = (event.startsAt - leadMinutes.coerceAtLeast(0) * 60_000L).coerceAtLeast(System.currentTimeMillis() + 1_000L)
        val pending = pendingIntent(context, event)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
        }
    }

    fun cancel(context: Context, event: EventRecord) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, event))
    }

    private fun pendingIntent(context: Context, event: EventRecord): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(EXTRA_EVENT_ID, event.id)
            .putExtra(EXTRA_TITLE, event.title)
            .putExtra(EXTRA_START, event.startsAt)
        return PendingIntent.getBroadcast(context, event.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
