package com.shiv.assistant.diagnostics

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.biometric.BiometricManager

data class PermissionReading(val title: String, val status: String, val revokeHelp: String)

/** Reads real Android grants. Never implies that optional, unsupported services exist. */
object ShivPermissionInspector {
    fun read(context: Context): List<PermissionReading> {
        val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val notifications = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        val listener = NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
        val credential = BiometricManager.from(context).canAuthenticate(
            if (Build.VERSION.SDK_INT >= 30)
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            else BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
        fun entry(name: String, enabled: Boolean, how: String) =
            PermissionReading(name, if (enabled) "GRANTED / AVAILABLE" else "NOT GRANTED", how)
        return listOf(
            entry("Microphone", mic, "Galaxy Settings > Apps > Shiv > Permissions > Microphone > Don't allow"),
            entry("Reminder notifications", notifications,
                "Galaxy Settings > Apps > Shiv > Notifications > Off"),
            entry("Notification Access", listener,
                "Galaxy Settings > Notifications > Advanced settings > Notification access > Shiv > Off"),
            entry("Device credential / biometrics", credential,
                "Galaxy Settings > Security and privacy > Biometrics. Shiv cannot change system biometrics."),
            PermissionReading("Internet (cloud AI exception)", "MANIFEST-DECLARED",
                "Not a runtime grant; only select Cloud/Hybrid and explicitly consent in Shiv Settings."),
            PermissionReading("Accessibility and screen capture", "NOT IMPLEMENTED / NOT ENABLED",
                "Shiv does not declare an AccessibilityService or request MediaProjection."),
            PermissionReading("Contacts, SMS, location, call log", "NOT REQUESTED",
                "Those optional integrations are not active in this build."),
            PermissionReading("Privileged shell / Shizuku", "NOT INCLUDED",
                "No ADB or Shizuku shell control is installed by Shiv.")
        )
    }
    fun applicationSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
