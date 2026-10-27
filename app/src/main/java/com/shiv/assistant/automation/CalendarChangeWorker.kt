package com.shiv.assistant.automation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.work.*
import com.shiv.assistant.data.ShivRepository
import com.shiv.assistant.data.ShivVault
import com.shiv.assistant.integrations.CalendarAdapter
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Owner-enabled read-only scan. Facts enter the encrypted signal queue first. */
class CalendarChangeWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
      return try {
        val context=applicationContext
        if(ShivVault(context).get(OPT_IN)!="yes" ||
            context.checkSelfPermission(Manifest.permission.READ_CALENDAR)!=PackageManager.PERMISSION_GRANTED)
            return Result.success()
        val vault=ShivVault(context)
        val previous=JSONObject(vault.get(SNAPSHOT)?:"{}")
        val current=JSONObject()
        val repository=ShivRepository(context)
        for(item in CalendarAdapter(context).inspectNext48Hours()) {
            val signature="${item.title}|${item.startAt}|${item.endAt}"
            current.put(item.id,signature)
            if(previous.has(item.id) && previous.optString(item.id)!=signature) {
                repository.incoming("calendar-change",
                    "Updated calendar item: ${item.title} at ${item.startAt}",5)
            } else if(!previous.has(item.id) && vault.get(SNAPSHOT)!=null) {
                repository.incoming("calendar-change",
                    "New calendar item: ${item.title} at ${item.startAt}",5)
            }
        }
        // A newly entering 48-hour window is treated as a new item. Absence
        // alone is not a deletion because Android's provider can be transient.
        vault.put(SNAPSHOT,current.toString())
        Result.success()
      } catch (_:Exception) { Result.retry() }
    }

    companion object {
        const val OPT_IN="calendar_change_opt_in"
        private const val SNAPSHOT="calendar_change_snapshot"
        private const val WORK="shiv-calendar-change-monitor"
        fun updateRegistration(context:Context,enabled:Boolean) {
            val manager=WorkManager.getInstance(context)
            if(enabled) manager.enqueueUniquePeriodicWork(WORK,ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<CalendarChangeWorker>(15,TimeUnit.MINUTES).build())
            else manager.cancelUniqueWork(WORK)
        }
    }
}
