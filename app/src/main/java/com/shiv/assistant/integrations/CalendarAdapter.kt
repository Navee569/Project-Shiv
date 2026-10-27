package com.shiv.assistant.integrations

import android.Manifest
import android.content.Context
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.util.TimeZone
import com.shiv.assistant.data.EventRecord
import com.shiv.assistant.domain.SensitiveAppDenylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A temporary preview. External events enter Shiv's encrypted DB ONLY after owner review. */
data class CalendarCandidate(
    val id: String, val title: String, val startAt: Long, val endAt: Long
)
data class WritableCalendar(val id:Long,val name:String)

object CalendarExportPolicy {
    fun approved(event:EventRecord,now:Long):Boolean =
        event.source!="owner-reviewed-calendar" &&
            CalendarImportPolicy.sanitizeTitle(event.title)!=null &&
            event.startAt in now..(now+14L*86400_000L) &&
            (event.endAt==null || event.endAt>event.startAt)
}

object CalendarImportPolicy {
    /** Fail closed on potentially sensitive notification/calendar contents. */
    fun sanitizeTitle(title: String?): String? {
        val value = title?.trim() ?: return null
        if (value.length !in 3..180) return null
        if (Regex("(?i)\\b(otp|password|passcode|pin|cvv|recovery.code|verification.code|account.number)\\b")
                .containsMatchIn(value)) return null
        if (Regex("\\b[0-9]{9,}\\b").containsMatchIn(value)) return null
        return value
    }
    fun accept(preview: CalendarCandidate, now: Long): EventRecord? {
        val text = sanitizeTitle(preview.title) ?: return null
        if (preview.startAt !in now..(now + 14L * 86400_000L)) return null
        if (preview.endAt < preview.startAt) return null
        if (!preview.id.matches(Regex("external-[0-9]+-[0-9]+"))) return null
        return EventRecord(preview.id, text, preview.startAt, preview.endAt,
            source = "owner-reviewed-calendar", confidence = 1.0)
    }
}
class CalendarAdapter(private val context: Context) {
    private fun granted():Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED &&
        context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR)==PackageManager.PERMISSION_GRANTED

    suspend fun writableCalendars():List<WritableCalendar> = withContext(Dispatchers.IO) {
        if(!granted()) return@withContext emptyList()
        val found=mutableListOf<WritableCalendar>()
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID,CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,CalendarContract.Calendars.VISIBLE),
            null,null,null)?.use { cursor ->
            while(cursor.moveToNext() && found.size<30) {
                if(cursor.getInt(2)>=CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR &&
                    cursor.getInt(3)==1 && cursor.getLong(0)>0L)
                    found.add(WritableCalendar(cursor.getLong(0),cursor.getString(1)?:"Calendar"))
            }
        }
        found
    }

    suspend fun writeApprovedEvent(event:EventRecord,calendarId:Long):Long = withContext(Dispatchers.IO) {
        require(granted()) { "Calendar permission revoked" }
        require(CalendarExportPolicy.approved(event,System.currentTimeMillis())) { "Event cannot be exported" }
        require(writableCalendars().any { it.id==calendarId }) { "Calendar is no longer writable" }
        val values=ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID,calendarId)
            put(CalendarContract.Events.TITLE,event.title.trim())
            put(CalendarContract.Events.DTSTART,event.startAt)
            put(CalendarContract.Events.DTEND,event.endAt ?: event.startAt+3600_000L)
            put(CalendarContract.Events.EVENT_TIMEZONE,TimeZone.getDefault().id)
        }
        val uri=context.contentResolver.insert(CalendarContract.Events.CONTENT_URI,values)
            ?: error("Calendar provider refused the event")
        ContentUris.parseId(uri).takeIf { it>0L } ?: error("Calendar provider returned no event ID")
    }
    suspend fun inspectNext48Hours(): List<CalendarCandidate> = withContext(Dispatchers.IO) {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) !=
            PackageManager.PERMISSION_GRANTED) return@withContext emptyList()
        val start = System.currentTimeMillis()
        val end = start + 48L * 3600_000L
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        android.content.ContentUris.appendId(builder, start)
        android.content.ContentUris.appendId(builder, end)
        val items = mutableListOf<CalendarCandidate>()
        val result=context.contentResolver.query(builder.build(),
            arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN, CalendarContract.Instances.END),
            null, null, CalendarContract.Instances.BEGIN + " ASC")
            ?: error("Calendar provider unavailable")
        result.use { cursor ->
            while (cursor.moveToNext() && items.size < 50) {
                val id = cursor.getLong(0)
                val title = CalendarImportPolicy.sanitizeTitle(cursor.getString(1)) ?: continue
                val starts = cursor.getLong(2)
                val ends = cursor.getLong(3)
                if (id > 0L && starts in start..end && ends >= starts) {
                    items.add(CalendarCandidate("external-$id-$starts", title, starts, ends))
                }
            }
        }
        items.distinctBy { it.id }
    }
}
