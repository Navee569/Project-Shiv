package app.shiv

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    fun schedule(context: Context, event: EventRecord, leadMinutes: Int = Brain.DEFAULT_LEAD_MINUTES) =
        AlarmScheduler.schedule(context.applicationContext, event, leadMinutes)
}
