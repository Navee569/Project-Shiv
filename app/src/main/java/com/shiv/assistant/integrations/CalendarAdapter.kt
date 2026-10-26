package com.shiv.assistant.integrations

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import com.shiv.assistant.data.EventRecord
import com.shiv.assistant.domain.SensitiveAppDenylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A temporary preview. External events enter Shiv's encrypted DB ONLY after owner review. */
data class CalendarCandidate(
    val id: String, val title: String, val startAt: Long, val endAt: Long
)

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
    suspend fun inspectNext48Hours(): List<CalendarCandidate> = withContext(Dispatchers.IO) {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) !=
            PackageManager.PERMISSION_GRANTED) return@withContext emptyList()
        val start = System.currentTimeMillis()
        val end = start + 48L * 3600_000L
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        android.content.ContentUris.appendId(builder, start)
        android.content.ContentUris.appendId(builder, end)
        val items = mutableListOf<CalendarCandidate>()
        context.contentResolver.query(builder.build(),
            arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN, CalendarContract.Instances.END),
            null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { cursor ->
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
