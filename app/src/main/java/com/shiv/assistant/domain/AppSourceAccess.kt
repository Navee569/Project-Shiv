package com.shiv.assistant.domain

import android.content.Context
import com.shiv.assistant.data.ShivVault
import java.util.Locale

enum class AppSourceMode { INCLUDE, NOTIFICATIONS_ONLY, EXCLUDE }

/** Owner policy applies before reading a notification or screen tree, then again at storage. */
object AppSourceAccess {
    const val VAULT_KEY = "app_source_modes_v1"

    fun decode(stored: String?): Map<String, AppSourceMode> {
        if (stored == null || stored.isEmpty()) return emptyMap()
        require(stored.isNotBlank())
        val result = linkedMapOf<String, AppSourceMode>()
        for (line in stored.lineSequence()) {
            val parts = line.split('=', limit = 2)
            require(parts.size == 2 && SourceExclusions.validPackage(parts[0]))
            val app = parts[0].lowercase(Locale.ROOT)
            require(app !in result && result.size < 100)
            result[app] = AppSourceMode.valueOf(parts[1])
        }
        return result
    }

    fun encode(modes: Map<String, AppSourceMode>): String {
        require(modes.size <= 100 && modes.keys.all {
            SourceExclusions.validPackage(it) && it == it.lowercase(Locale.ROOT)
        })
        return modes.toSortedMap().entries.joinToString("\n") { (app, mode) ->
            app.lowercase(Locale.ROOT) + "=" + mode.name
        }
    }

    fun permits(packageName: String, source: String,
                modes: Map<String, AppSourceMode>): Boolean {
        val app = packageName.trim().lowercase(Locale.ROOT)
        if (!SourceExclusions.validPackage(app) || SensitiveAppDenylist.excluded(app)) return false
        return when (modes[app] ?: AppSourceMode.INCLUDE) {
            AppSourceMode.INCLUDE -> source == "notification" || source == "accessibility"
            AppSourceMode.NOTIFICATIONS_ONLY -> source == "notification"
            AppSourceMode.EXCLUDE -> false
        }
    }

    /** Decryption and malformed-policy failures deny capture rather than widening access. */
    fun mayRead(context: Context, packageName: String, source: String): Boolean = try {
        val vault = ShivVault(context)
        val ownerDenied=SourceExclusions.fromString(vault.get("app_denied_packages"))
        !SourceExclusions.excluded(packageName, ownerDenied) &&
            permits(packageName, source, decode(vault.get(VAULT_KEY)))
    } catch (_: Exception) { false }
}
