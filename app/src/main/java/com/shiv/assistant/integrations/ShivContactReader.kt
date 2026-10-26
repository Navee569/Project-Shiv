package com.shiv.assistant.integrations

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import com.shiv.assistant.domain.PrivacyPolicy

/** Explicit owner search; returns a few display names transiently, no bulk scraping. */
class ShivContactReader(private val context: Context) {
    fun searchByName(query: String): List<String> {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED || query.trim().length < 2) return emptyList()
        val names = mutableListOf<String>()
        val pattern = "%" + query.trim().take(60).replace("%", "\\%").replace("_", "\\_") + "%"
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " LIKE ?",
            arrayOf(pattern), ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC"
        )?.use { cursor ->
            while (cursor.moveToNext() && names.size < 10) {
                val safe = PrivacyPolicy.safeExtractedFact("android.contacts", cursor.getString(0))
                if (safe != null) names.add(safe)
            }
        }
        return names.distinct().take(10)
    }
}
