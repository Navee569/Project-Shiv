package com.shiv.assistant.automation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import com.shiv.assistant.domain.ActionKind
import com.shiv.assistant.domain.ActionProposal
import com.shiv.assistant.domain.Risk
import java.net.URLEncoder

/**
 * Android actions are bounded, transparent handoffs.
 * Calls, messages, payments and posts are never silently committed.
 */
object ShivActions {
    fun launch(context: Context, proposal: ActionProposal): Boolean {
        if (proposal.risk == Risk.RESTRICTED) return false
        val value = proposal.argument.trim()
        val uriEncode = { s: String -> Uri.encode(s) }
        val intent = when (proposal.kind) {
            ActionKind.DIAL -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + uriEncode(value)))
            ActionKind.SMS -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).putExtra("sms_body", value)
            ActionKind.EMAIL -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).putExtra(Intent.EXTRA_TEXT, value)
            ActionKind.MAPS -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + uriEncode(value)))
            ActionKind.CALENDAR -> Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, value)
            ActionKind.ALARM -> Intent(AlarmClock.ACTION_SET_ALARM)
            ActionKind.TIMER -> Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, value.toIntOrNull()?.coerceIn(1, 86400) ?: 60)
            ActionKind.CAMERA -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            ActionKind.VIDEO -> Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://www.youtube.com/results?search_query=" + uriEncode(value)))
            ActionKind.MUSIC -> Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://music.youtube.com/search?q=" + uriEncode(value)))
            ActionKind.SETTINGS -> Intent(
                when {
                    value.lowercase().contains("wifi") -> Settings.ACTION_WIFI_SETTINGS
                    value.lowercase().contains("bluetooth") -> Settings.ACTION_BLUETOOTH_SETTINGS
                    else -> Settings.ACTION_SETTINGS
                })
            ActionKind.SMART_HOME -> Intent(Intent.ACTION_VIEW, Uri.parse("https://home.google.com"))
            ActionKind.WEB -> {
                val safeUrl = if (value.startsWith("https://")) value else "https://" + value
                Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl))
            }
            ActionKind.UPI -> Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay"))
            ActionKind.TELEGRAM, ActionKind.WHATSAPP, ActionKind.SHARE -> Intent(Intent.ACTION_SEND)
                .setType("text/plain").putExtra(Intent.EXTRA_TEXT, value)
            else -> return false
        }
        if (proposal.kind == ActionKind.TELEGRAM) intent.setPackage("org.telegram.messenger")
        if (proposal.kind == ActionKind.WHATSAPP) intent.setPackage("com.whatsapp")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            // Intentionally do not redirect sensitive content to an unintended app.
            false
        } catch (_: SecurityException) { false }
    }
}
