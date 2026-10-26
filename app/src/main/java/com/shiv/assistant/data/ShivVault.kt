package com.shiv.assistant.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted local persistence. AES key never leaves the Android Keystore.
 * There are no unencrypted credentials, memories, or conversation bodies in SharedPreferences.
 */
class ShivVault(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = context.applicationContext.getSharedPreferences("shiv_encrypted_v1", Context.MODE_PRIVATE)
    private val alias = "shiv_vault_master_v1"
    private val key: SecretKey by lazy {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        ).apply {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }
    @Synchronized
    private fun putLegacy(name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        val bytes = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        check(preferences.edit().putString(name, Base64.encodeToString(bytes, Base64.NO_WRAP)).commit()) {
            "Unable to persist encrypted data"
        }
    }
    @Synchronized
    private fun getLegacy(name: String): String? {
        val source = preferences.getString(name, null) ?: return null
        try {
            val bytes = Base64.decode(source, Base64.DEFAULT)
            require(bytes.size > 28) { "Invalid ciphertext" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
            return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        } catch (e: Exception) {
            // Never silently overwrite corrupted data. UI can recover or erase explicitly.
            throw IllegalStateException("Encrypted Shiv record unavailable: " + name, e)
        }
    }
    private val secureEntries: SecureEntries by lazy {
        val existing = getLegacy("__sqlcipher_passphrase_v1")
        val bytes = if (existing != null) {
            Base64.decode(existing, Base64.NO_WRAP).also {
                require(it.size == 32) { "Encrypted database secret invalid" }
            }
        } else {
            val candidate = ByteArray(32).also(SecureRandom()::nextBytes)
            putLegacy("__sqlcipher_passphrase_v1",
                Base64.encodeToString(candidate, Base64.NO_WRAP))
            candidate
        }
        // SQLCipher's factory needs this passphrase for possible database reopen.
        // Do not wipe the array before the first lazy DB open.
        ShivRoomDatabase.open(appContext, bytes).entries()
    }

    /**
     * Lazy, lossless migration: read each legacy Keystore-encrypted value,
     * commit it into SQLCipher, and only then remove the original ciphertext.
     */
    @Synchronized
    fun get(name: String): String? {
        require(name.isNotBlank() && name != "__sqlcipher_passphrase_v1")
        val stored = secureEntries.read(name)
        if (stored != null) return stored
        val legacy = getLegacy(name) ?: return null
        secureEntries.write(SecureEntry(name, legacy))
        check(preferences.edit().remove(name).commit()) { "Legacy cleanup failed" }
        return legacy
    }

    @Synchronized
    fun put(name: String, value: String) {
        require(name.isNotBlank() && name != "__sqlcipher_passphrase_v1")
        secureEntries.write(SecureEntry(name, value))
        // Remove any Keystore-encrypted legacy copy only after SQLCipher commit.
        if (preferences.contains(name))
            check(preferences.edit().remove(name).commit()) { "Legacy cleanup failed" }
    }
    fun notes(): List<String> {
        val data = get("notes") ?: return emptyList()
        val json = JSONArray(data)
        return (0 until json.length()).map { json.getString(it) }
    }
    fun addNote(note: String) {
        require(note.length in 1..8192)
        val list = (notes() + note.trim()).takeLast(250)
        put("notes", JSONArray(list).toString())
    }
    fun history(): List<Pair<String, String>> {
        val json = JSONArray(get("history") ?: "[]")
        return (0 until json.length()).map {
            val row = json.getJSONObject(it)
            row.getString("role") to row.getString("body")
        }
    }
    fun addChat(role: String, message: String) {
        require(role in setOf("user", "assistant"))
        val copy = history().takeLast(99) + (role to message.take(12000))
        val json = JSONArray()
        copy.forEach { (r, m) -> json.put(JSONObject().put("role", r).put("body", m)) }
        put("history", json.toString())
    }
    fun clearHistory() { put("history", "[]") }
    fun addNotificationSummary(source: String, title: String) {
        val json = JSONArray(get("notifications") ?: "[]")
        val next = JSONArray()
        for (i in maxOf(0, json.length() - 48) until json.length()) next.put(json.get(i))
        next.put(JSONObject().put("app", source.take(100)).put("title", title.take(200)))
        put("notifications", next.toString())
    }
    fun notificationCount(): Int = JSONArray(get("notifications") ?: "[]").length()
    fun clearNotifications() { put("notifications", "[]") }
}
