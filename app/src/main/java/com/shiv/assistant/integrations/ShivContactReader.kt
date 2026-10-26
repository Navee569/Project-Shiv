package com.shiv.assistant.integrations

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.net.Uri
import com.shiv.assistant.domain.PrivacyPolicy

/** Explicit owner search; returns a few display names transiently, no bulk scraping. */
class ShivContactReader(private val context: Context) {
    fun searchByName(query: String): List<String> {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED || query.trim().length < 2) return emptyList()
        val names = mutableListOf<String>()
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI,
            Uri.encode(query.trim().take(60)))
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            null, null, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC"
        )?.use { cursor ->
            while (cursor.moveToNext() && names.size < 10) {
                val safe = PrivacyPolicy.safeExtractedFact("android.contacts", cursor.getString(0))
                if (safe != null) names.add(safe)
            }
        }
        return names.distinct().take(10)
    }
}
