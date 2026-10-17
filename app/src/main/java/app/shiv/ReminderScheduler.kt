package app.shiv

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    fun schedule(context: Context, event: MemoryEvent) {
        val at = event.reminderAt ?: return
        val delay = (at - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).setInputData(workDataOf("title" to event.title)).build()
        WorkManager.getInstance(context).enqueueUniqueWork("reminder-" + event.id, ExistingWorkPolicy.REPLACE, request)
    }
}
