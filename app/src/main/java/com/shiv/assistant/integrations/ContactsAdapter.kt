package com.shiv.assistant.integrations

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Temporary owner-triggered name matching; never reads phones/emails, syncs or stores contacts. */
object ContactNamePolicy {
    fun safeQuery(query:String):String? {
        val value=query.trim()
        if(value.length !in 2..60) return null
        if(value.any { it.isISOControl() }) return null
        if(Regex("(?i)\\b(otp|pin|password|cvv|recovery.code)\\b").containsMatchIn(value))
            return null
        return value
    }
    fun safeName(name:String?):String? {
        val value=name?.trim() ?: return null
        if(value.length !in 2..100 || value.any { it.isISOControl() })return null
        if(Regex("\\b[0-9]{7,}\\b").containsMatchIn(value))return null
        return value
    }
}
class ContactsAdapter(private val context:Context) {
    suspend fun matchNames(query:String):List<String> = withContext(Dispatchers.IO) {
        val safe=ContactNamePolicy.safeQuery(query) ?: return@withContext emptyList()
        if(context.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=
            PackageManager.PERMISSION_GRANTED) return@withContext emptyList()
        val uri=Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI,
            Uri.encode(safe))
        val names=mutableListOf<String>()
        context.contentResolver.query(uri,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            null,null,ContactsContract.Contacts.DISPLAY_NAME_PRIMARY+" ASC")?.use { cursor ->
            while(cursor.moveToNext() && names.size<10) {
                val name=ContactNamePolicy.safeName(cursor.getString(0))
                if(name!=null) names.add(name)
            }
        }
        names.distinct()
    }
}
