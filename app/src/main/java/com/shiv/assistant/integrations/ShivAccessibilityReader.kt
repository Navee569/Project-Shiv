package com.shiv.assistant.integrations

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.shiv.assistant.automation.ShivSignalWorker
import com.shiv.assistant.data.ShivSignalQueue
import com.shiv.assistant.data.ShivVault
import com.shiv.assistant.domain.PrivacyPolicy
import com.shiv.assistant.domain.SignalFacts

/**
 * User-armed, short-lived Accessibility capture. Only extracted event facts
 * from non-denied packages are queued. No raw screen text or view hierarchy
 * is retained. Sensitive/secure windows may be entirely inaccessible on Android.
 */
class ShivAccessibilityReader : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        try {
            val vault = ShivVault(applicationContext)
            val armedUntil = vault.get("screen_armed_until")?.toLongOrNull() ?: 0L
            if (armedUntil <= System.currentTimeMillis() ||
                armedUntil > System.currentTimeMillis() + 60_000L) return
            if (!PrivacyPolicy.canSense(
                    vault.get("sensing_paused") == "yes",
                    vault.get("guest_mode") == "yes",
                    vault.get("privacy_zone") == "yes")) return
            val source = event.packageName?.toString() ?: return
            val exclusions = vault.get("denied_packages").orEmpty().lineSequence()
                .map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
            // Source package is checked BEFORE touching any node or event text.
            val allowlist = vault.get("screen_allowed_packages").orEmpty().lineSequence()
                .map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
            // Fail closed unless the owner allowed this precise package beforehand.
            if (source.lowercase() !in allowlist ||
                PrivacyPolicy.isDeniedPackage(source, exclusions) ||
                event.isPassword || event.source?.isPassword == true) return
            val candidate = event.text.firstOrNull()?.toString() ?: return
            // Fail closed on account numbers or OTP-like digits even without labels.
            if (candidate.length !in 5..200 ||
                Regex("\\d{4,}").containsMatchIn(candidate)) return
            val allowed = PrivacyPolicy.safeExtractedFact(source, candidate, exclusions) ?: return
            val fact = SignalFacts.extract(allowed) ?: return
            if (fact.confidence < 0.5) return
            val queue = ShivSignalQueue(vault)
            if (queue.enqueue(source, fact.summary, exclusions) != null) {
                vault.put("screen_armed_until", "0")
                WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                    "shiv_encrypted_signal_worker", ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<ShivSignalWorker>().build())
            }
        } catch (_: Exception) {
            // No raw screen data or exception messages enter Android logs.
        }
    }
    override fun onInterrupt() {}
}
