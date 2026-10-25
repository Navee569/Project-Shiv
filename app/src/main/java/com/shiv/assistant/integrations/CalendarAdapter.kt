package com.shiv.assistant.integrations

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import com.shiv.assistant.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read calendar only when READ_CALENDAR has actually been granted by owner. */
class CalendarAdapter(private val context:Context) {
    suspend fun inspectNext48Hours():List<EventRecord> = withContext(Dispatchers.IO) {
        if(context.checkSelfPermission(Manifest.permission.READ_CALENDAR)!=PackageManager.PERMISSION_GRANTED)
            return@withContext emptyList()
        val start=System.currentTimeMillis()
        val end=start+48*3600_000L
        val builder=CalendarContract.Instances.CONTENT_URI.buildUpon()
        android.content.ContentUris.appendId(builder,start)
        android.content.ContentUris.appendId(builder,end)
        val events=mutableListOf<EventRecord>()
        context.contentResolver.query(builder.build(),
            arrayOf(CalendarContract.Instances.EVENT_ID,CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,CalendarContract.Instances.END),
            null,null,CalendarContract.Instances.BEGIN+" ASC")?.use { cursor ->
            while(cursor.moveToNext() && events.size<50) {
                val id=cursor.getLong(0)
                val title=cursor.getString(1)?:continue
                events.add(EventRecord("external-$id-"+cursor.getLong(2),title.take(180),
                    cursor.getLong(2),cursor.getLong(3),"calendar",1.0))
            }
        }
        events
    }
}
