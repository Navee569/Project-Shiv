package app.shiv

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import app.shiv.domain.repository.ShivViewRepository

object CalendarSyncer {
    fun sync(context: Context, dao: ShivViewRepository): Int {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return 0
        val now = System.currentTimeMillis()
        val end = now + 90L * 24 * 60 * 60 * 1000
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now - 7L * 24 * 60 * 60 * 1000)
            ContentUris.appendId(it, end)
        }.build()
        val columns = arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.EVENT_LOCATION)
        val events = mutableListOf<EventRecord>()
        context.contentResolver.query(uri, columns, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
            val eventId = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val calendarId = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)
            val title = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val begin = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val location = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
            while (cursor.moveToNext()) {
                val starts = cursor.getLong(begin)
                events += EventRecord(
                    id = "calendar-${cursor.getLong(calendarId)}-${cursor.getLong(eventId)}-$starts",
                    title = cursor.getString(title) ?: "Calendar event",
                    startsAt = starts,
                    place = cursor.getString(location)?.takeIf { it.isNotBlank() },
                    source = "CALENDAR"
                )
            }
        }
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            dao.calendarEvents().forEach { AlarmScheduler.cancel(context, it) }
            dao.clearCalendarEvents()
            events.forEach { dao.saveEvent(it) }
            val lead = dao.reminderRule(Brain.REMINDER_LEAD_KEY)?.value?.toIntOrNull() ?: Brain.DEFAULT_LEAD_MINUTES
            events.forEach { AlarmScheduler.schedule(context, it, lead) }
            dao.logAction(ActionLogEntry(action = "Synced phone calendar", tier = ActionTier.FREE.name, outcome = "${events.size} events saved locally"))
        }
        return events.size
    }
}
