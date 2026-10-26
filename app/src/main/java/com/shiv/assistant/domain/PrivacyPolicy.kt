package com.shiv.assistant.domain

import java.util.Locale

/**
 * Security boundary: untrusted application signals must pass this BEFORE persistence,
 * audit logging, context construction, inference, or notification display.
 * Blocking is intentionally conservative; the owner can add, never remove, exclusions.
 */
object PrivacyPolicy {
    private val blockedPackages = setOf(
        "com.google.android.apps.authenticator2",
        "com.azure.authenticator", "com.microsoft.authenticator",
        "com.authy.authy", "com.lastpass.authenticator",
        "com.onepassword.android", "com.agilebits.onepassword",
        "com.bitwarden.mobile", "com.x8bit.bitwarden",
        "com.lastpass.lpandroid", "com.dashlane", "com.nordpass.android.app",
        "com.roboform", "com.keepersecurity.keeper",
        "com.google.android.apps.walletnfcrel", "com.google.android.apps.nbu.paisa.user",
        "com.phonepe.app", "net.one97.paytm", "in.org.npci.upiapp",
        "com.amazon.mShop.android.shopping", "com.mobikwik_new",
        "com.freecharge.android", "com.csam.icici.bank.imobile",
        "com.sbi.lotusintouch", "com.sbi.SBIFreedomPlus",
        "com.axis.mobile", "com.hdfcbank.payzapp",
        "com.snapwork.hdfc", "com.kotak.bank.mobile",
        "com.idfcfirstbank.optimus", "com.rblbank.mobank",
        "com.indusind.indie", "com.bharatpe.app",
        "com.cred.android", "com.sliceit.android"
    )
    private val sensitivePackageTerms = setOf(
        "bank", "wallet", "payment", "paytm", "phonepe", "paisa",
        "upi", "authenticator", "password", "passmanager", "otp",
        "creditcard", "debitcard", "investment", "broker", "finance"
    )
    private val sensitiveText = listOf(
        Regex("(?i)\\b(?:otp|one.time password|verification code|cvv|pin|password|passcode|recovery code)\\b"),
        Regex("(?i)\\b(?:account|card)\\s*(?:no\\.?|number|ending)\\s*[:#]?\\s*\\d{4,}"),
        Regex("\\b\\d{12,19}\\b")
    )
    fun isDeniedPackage(packageName: String, additionalDenied: Set<String> = emptySet()): Boolean {
        val name = packageName.trim().lowercase(Locale.ROOT)
        if (name.isBlank()) return true
        if (name in blockedPackages || name in additionalDenied) return true
        if (blockedPackages.any { name.startsWith(it + ".") }) return true
        return name.split('.','_','-').any { segment ->
            sensitivePackageTerms.any { term -> segment.contains(term) }
        }
    }
    fun safeNotificationTitle(
        packageName: String, title: String?, additionalDenied: Set<String> = emptySet()
    ): String? {
        if (isDeniedPackage(packageName, additionalDenied)) return null
        val value = title?.trim()?.take(200) ?: return null
        if (value.isBlank() || sensitiveText.any { it.containsMatchIn(value) }) return null
        return value
    }
    fun canSense(paused: Boolean, guestMode: Boolean, privacyZone: Boolean) =
        !paused && !guestMode && !privacyZone

    fun safeExtractedFact(source: String, fact: String?, additionalDenied: Set<String> = emptySet()): String? {
        if (isDeniedPackage(source, additionalDenied)) return null
        val value = fact?.trim()?.take(256) ?: return null
        if (value.isBlank() || sensitiveText.any { it.containsMatchIn(value) }) return null
        return value
    }
}
