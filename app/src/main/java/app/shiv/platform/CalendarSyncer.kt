package app.shiv.platform

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import app.shiv.domain.integration.CalendarImportRepository
import app.shiv.domain.integration.CalendarSyncFailure
import app.shiv.domain.integration.CalendarSyncResult
import app.shiv.domain.integration.ReminderSchedulingPort
import app.shiv.domain.model.EventRecord
import kotlinx.coroutines.CancellationException

internal fun interface CalendarEventSource {
    /** Null means the provider did not return a usable cursor; it is never interpreted as empty. */
    suspend fun upcomingEvents(): List<EventRecord>?
}

object CalendarSyncer {
    internal suspend fun sync(
        source: CalendarEventSource,
        repository: CalendarImportRepository,
        reminders: ReminderSchedulingPort
    ): CalendarSyncResult {
        val imported = try {
            source.upcomingEvents() ?: return CalendarSyncResult(
                failed = true,
                failureReason = CalendarSyncFailure.PROVIDER_UNAVAILABLE
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return CalendarSyncResult(failed = true, failureReason = CalendarSyncFailure.PROVIDER_UNAVAILABLE)
        }

        val (previous, leadMinutes) = try {
            repository.calendarEvents() to (
                repository.reminderRule(Brain.REMINDER_LEAD_KEY)?.value?.toIntOrNull()
                    ?: Brain.DEFAULT_LEAD_MINUTES
                )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return CalendarSyncResult(failed = true, failureReason = CalendarSyncFailure.EVENT_PERSISTENCE)
        }

        try {
            repository.replaceCalendarEvents(imported)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return CalendarSyncResult(failed = true, failureReason = CalendarSyncFailure.EVENT_PERSISTENCE)
        }

        try {
            previous.forEach(reminders::cancel)
            imported.forEach { reminders.schedule(it, leadMinutes) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return CalendarSyncResult(
                importedCount = imported.size,
                failed = true,
                eventsUpdated = true,
                failureReason = CalendarSyncFailure.REMINDER_SCHEDULING
            )
        }

        try {
            repository.recordCalendarImport(imported.size)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return CalendarSyncResult(
                importedCount = imported.size,
                failed = true,
                eventsUpdated = true,
                remindersUpdated = true,
                failureReason = CalendarSyncFailure.AUDIT_WRITE
            )
        }

        return CalendarSyncResult(
            importedCount = imported.size,
            eventsUpdated = true,
            remindersUpdated = true
        )
    }
}

internal class AndroidCalendarEventSource(private val context: Context) : CalendarEventSource {
    override suspend fun upcomingEvents(): List<EventRecord>? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Calendar permission is unavailable")
        }
        val now = System.currentTimeMillis()
        val end = now + 90L * 24 * 60 * 60 * 1000
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now - 7L * 24 * 60 * 60 * 1000)
            ContentUris.appendId(it, end)
        }.build()
        val columns = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.EVENT_LOCATION
        )
        val result = mutableListOf<EventRecord>()
        val cursor = context.contentResolver.query(
            uri,
            columns,
            null,
            null,
            "${CalendarContract.Instances.BEGIN} ASC"
        ) ?: return null
        cursor.use {
            val eventId = it.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val calendarId = it.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)
            val title = it.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val begin = it.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val location = it.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
            while (it.moveToNext()) {
                val starts = it.getLong(begin)
                result += EventRecord(
                    id = "calendar-${it.getLong(calendarId)}-${it.getLong(eventId)}-$starts",
                    title = it.getString(title) ?: "Calendar event",
                    startsAt = starts,
                    place = it.getString(location)?.takeIf(String::isNotBlank),
                    source = "CALENDAR"
                )
            }
        }
        return result
    }
}
