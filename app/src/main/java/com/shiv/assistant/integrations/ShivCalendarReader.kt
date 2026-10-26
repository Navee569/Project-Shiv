package com.shiv.assistant.integrations

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import com.shiv.assistant.domain.PrivacyPolicy

/** One-shot preview only, never persisted unless the owner imports selected records. */
data class CalendarPreview(val eventId: Long, val title: String, val startMillis: Long)

class ShivCalendarReader(private val context: Context) {
    fun upcoming(limit: Int = 30): List<CalendarPreview> {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) !=
            PackageManager.PERMISSION_GRANTED) return emptyList()
        val bounded = limit.coerceIn(1, 50)
        val start = System.currentTimeMillis()
        val end = start + 14L * 86_400_000L
        val columns = arrayOf(CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN)
        val result = mutableListOf<CalendarPreview>()
        CalendarContract.Instances.query(context.contentResolver, columns, start, end)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val timeIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            while (cursor.moveToNext() && result.size < bounded) {
                val eventId = cursor.getLong(idIndex)
                val time = cursor.getLong(timeIndex)
                val title = PrivacyPolicy.safeExtractedFact("android.calendar",
                    cursor.getString(titleIndex)) ?: continue
                if (eventId > 0L && time in start..end)
                    result.add(CalendarPreview(eventId, title, time))
            }
        }
        return result.distinctBy { it.eventId to it.startMillis }.take(bounded)
    }
}
