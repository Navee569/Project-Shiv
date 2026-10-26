package com.shiv.assistant.domain

import android.content.Context
import com.shiv.assistant.data.ShivVault
import java.util.Locale

/** Encrypted owner denylist. Every capture source is checked before reading its payload. */
object SourceExclusions {
    private val packagePattern=Regex("[a-z0-9_]+(?:[.][a-z0-9_]+)+")
    fun validPackage(value:String):Boolean =
        packagePattern.matches(value.trim().lowercase(Locale.ROOT))
    fun fromString(stored:String?):Set<String> = stored.orEmpty()
        .lineSequence().map { it.trim().lowercase(Locale.ROOT) }
        .filter { validPackage(it) }.take(100).toSet()
    fun excluded(source:String, added:Set<String>):Boolean =
        SensitiveAppDenylist.excluded(source,added)
    /** Fail closed on decryption/database errors, never bypass owner exclusions. */
    fun shouldBlock(context:Context,source:String):Boolean {
        if(SensitiveAppDenylist.excluded(source))return true
        return try {
            excluded(source,fromString(ShivVault(context).get("app_denied_packages")))
        } catch (_:Exception) { true }
    }
}
