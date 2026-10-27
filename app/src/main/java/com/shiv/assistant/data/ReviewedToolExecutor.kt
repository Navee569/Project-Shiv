package com.shiv.assistant.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.shiv.assistant.automation.ShivReminders
import com.shiv.assistant.domain.ParsedToolAction
import com.shiv.assistant.domain.ToolAction
import java.time.OffsetDateTime
import java.util.UUID

/** Executes only a previously parsed proposal after the owner presses Approve. */
class ReviewedToolExecutor(private val context:Context) {
    private val dao=EncryptedDatabase.open(context).dao()
    suspend fun execute(proposal:ParsedToolAction):String {
        require(proposal.needsReview && proposal.action!=ToolAction.STAY_QUIET)
        val a=proposal.arguments
        var undo="NOT_AVAILABLE"
        val result=when(proposal.action) {
            ToolAction.CREATE_EVENT -> {
                val start=instant(a.getValue("datetime"))
                val end=a["end"]?.let(::instant)
                require(start>System.currentTimeMillis() && (end==null || end>start))
                val id=UUID.randomUUID().toString()
                dao.putEvent(EventRecord(id,a.getValue("title"),start,end,
                    "owner-reviewed-model"))
                undo="EVENT:"+id
                "EVENT_SAVED"
            }
            ToolAction.UPDATE_EVENT -> {
                val old=dao.event(a.getValue("id")) ?: error("Event no longer exists")
                val start=a["datetime"]?.let(::instant) ?: old.startAt
                val end=a["end"]?.let(::instant) ?: old.endAt
                require(end==null || end>start)
                dao.putEvent(old.copy(title=a["title"]?:old.title,startAt=start,endAt=end))
                "EVENT_UPDATED"
            }
            ToolAction.SET_REMINDER -> {
                val due=instant(a.getValue("datetime"))
                require(due>System.currentTimeMillis())
                val id=UUID.randomUUID().toString()
                dao.putReminder(ReminderRecord(id,a.getValue("title"),due,null))
                ShivReminders.scheduleApprovedEvent(context,id,due)
                undo="REMINDER:"+id
                "REMINDER_SCHEDULED"
            }
            ToolAction.SAVE_MEMORY -> {
                val text=a.getValue("text")
                val category=a.getValue("category")
                val existing=dao.matchingOwnerMemory(text.trim(),category)
                val saved=ShivRepository(context).remember(text,category)
                if(existing==null)undo="MEMORY:"+saved.id
                "MEMORY_SAVED"
            }
            ToolAction.SAVE_RULE -> {
                require(a.getValue("rule")=="reminders_minute_of_day")
                val minute=a.getValue("value").toInt()
                require(minute in 0..1439)
                dao.putRule(RuleRecord("quiet-reminders-before",
                    "Ignore reminders before %02d:%02d".format(minute/60,minute%60),
                    "reminders_minute_of_day",minute.toString()))
                "RULE_SAVED"
            }
            ToolAction.ASK_USER -> "QUESTION: "+a.getValue("question")
            ToolAction.SEND_NOTIFICATION -> {
                val manager=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    !=PackageManager.PERMISSION_GRANTED) error("Notification permission denied")
                manager.createNotificationChannel(NotificationChannel("shiv_model_reviewed",
                    "Reviewed Shiv notifications",NotificationManager.IMPORTANCE_DEFAULT))
                manager.notify(UUID.randomUUID().hashCode(),NotificationCompat.Builder(context,"shiv_model_reviewed")
                    .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Shiv")
                    .setContentText(a.getValue("text")).setAutoCancel(true).build())
                "NOTIFICATION_SHOWN"
            }
            ToolAction.STAY_QUIET -> error("No action to execute")
        }
        dao.logAction(ActionLogRecord(UUID.randomUUID().toString(),proposal.action.name,
            "[REDACTED]","ASK_FIRST","OWNER_APPROVED",true,result,undo))
        return result
    }
    private fun instant(value:String):Long=OffsetDateTime.parse(value).toInstant().toEpochMilli()
}
