package com.shiv.assistant.integrations

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import com.shiv.assistant.MainActivity
import com.shiv.assistant.data.ShivAuditTrail
import com.shiv.assistant.data.ShivVault
import com.shiv.assistant.domain.CallPhase
import com.shiv.assistant.domain.PostCallPolicy
import com.shiv.assistant.domain.PrivacyPolicy
import java.time.LocalTime

/**
 * Best-effort Android call-state notification, NOT audio capture or a call log.
 * No caller ID, phone number, contact, transcript or raw call data is retained.
 */
class ShivCallCheckInReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        try {
            if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
                PackageManager.PERMISSION_GRANTED) return
            val next = when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
                TelephonyManager.EXTRA_STATE_IDLE -> CallPhase.IDLE
                TelephonyManager.EXTRA_STATE_OFFHOOK -> CallPhase.OFFHOOK
                TelephonyManager.EXTRA_STATE_RINGING -> CallPhase.RINGING
                else -> return
            }
            val vault = ShivVault(context)
            val previous = runCatching {
                CallPhase.valueOf(vault.get("call_phase") ?: "IDLE")
            }.getOrDefault(CallPhase.IDLE)
            val consent = vault.get("post_call_opt_in") == "yes"
            val sensing = PrivacyPolicy.canSense(vault.get("sensing_paused") == "yes",
                vault.get("guest_mode") == "yes", vault.get("privacy_zone") == "yes")
            // Only store a state enum. No person-specific data is ever retrieved.
            vault.put("call_phase", next.name)
            if (!PostCallPolicy.shouldPrompt(previous,next,consent,true,sensing,
                    LocalTime.now().hour)) return
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel("shiv_call_checkin",
                "Shiv optional call check-ins", NotificationManager.IMPORTANCE_DEFAULT))
            val action = PendingIntent.getActivity(context, 441, Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.notify(441, NotificationCompat.Builder(context,"shiv_call_checkin")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Shiv • After-call check-in")
                .setContentText("Was anything agreed? Open Shiv to add a task or event.")
                .setContentIntent(action).setAutoCancel(true).build())
            ShivAuditTrail(vault).add("POST_CALL", "Owner opted into a generic after-call question",
                "ACT_THEN_TELL", true)
        } catch (_: Exception) { /* No personal data in logs or thrown messages */ }
    }
}
