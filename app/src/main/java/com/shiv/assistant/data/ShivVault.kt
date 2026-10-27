package com.shiv.assistant.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import androidx.room.withTransaction

/**
 * Encrypted local persistence. AES key never leaves the Android Keystore.
 * There are no unencrypted credentials, memories, or conversation bodies in SharedPreferences.
 */
internal class LegacyShivVault(private val context: Context) {
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
    fun keys(): Set<String> = preferences.all.keys
    fun erase() { check(preferences.edit().clear().commit()) }
    @Synchronized
    fun put(name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        val bytes = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        check(preferences.edit().putString(name, Base64.encodeToString(bytes, Base64.NO_WRAP)).commit()) {
            "Unable to persist encrypted data"
        }
    }
    @Synchronized
    fun get(name: String): String? {
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

/**
 * Compatibility façade: current records live only inside SQLCipher.
 * Legacy AES-GCM preferences are read once and erased only after all records
 * have been copied successfully. Never fall back to plaintext storage.
 */
class ShivVault(private val context: Context) {
    private val dao by lazy { EncryptedDatabase.open(context).dao() }
    init {
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            val legacyStore = context.getSharedPreferences("shiv_encrypted_v1", Context.MODE_PRIVATE)
            if (legacyStore.all.isNotEmpty()) {
                val original = LegacyShivVault(context)
                LegacyVaultMigration.copyAtomically(EncryptedDatabase.open(context),
                    original.keys(),original::get)
                // The transaction committed before erasure. A crash here leaves
                // the marker in SQLCipher so a restart never overwrites newer edits.
                original.erase()
            }
        }
    }
    @Synchronized fun put(name:String,value:String) =
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            dao.putVault(VaultEntry(name,value))
        }
    @Synchronized fun get(name:String):String? =
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { dao.vault(name)?.value }
    fun notes(): List<String> {
        val array = org.json.JSONArray(get("notes") ?: "[]")
        return (0 until array.length()).map { array.getString(it) }
    }
    fun addNote(note:String) {
        require(note.isNotBlank() && note.length <= 8192)
        val compact=note.trim()
        val existing=notes()
        if(existing.any { it.equals(compact,ignoreCase=true) })return
        put("notes",org.json.JSONArray((existing+compact).takeLast(250)).toString())
    }
    fun history():List<Pair<String,String>> {
        val json = org.json.JSONArray(get("history")?:"[]")
        return (0 until json.length()).map { val row=json.getJSONObject(it)
            row.getString("role") to row.getString("body") }
    }
    fun addChat(role:String,message:String) {
        require(role=="user"||role=="assistant")
        val array=org.json.JSONArray()
        (history().takeLast(99)+(role to message.take(12000))).forEach { (r,m) ->
            array.put(org.json.JSONObject().put("role",r).put("body",m))
        }
        put("history",array.toString())
    }
    fun clearHistory() { put("history","[]") }
    fun addNotificationSummary(source:String,title:String) {
        val json=org.json.JSONArray(get("notifications")?:"[]")
        val next=org.json.JSONArray()
        for(i in maxOf(0,json.length()-48) until json.length())next.put(json.get(i))
        next.put(org.json.JSONObject().put("app",source.take(100)).put("title",title.take(200)))
        put("notifications",next.toString())
    }
    fun notificationCount()=org.json.JSONArray(get("notifications")?:"[]").length()
    fun clearNotifications()=put("notifications","[]")
}

/** A committed marker makes migration safe across crash/restart boundaries. */
internal object LegacyVaultMigration {
    const val MARKER="__legacy_vault_migrated_v1"
    suspend fun copyAtomically(database:ShivDatabase, keys:Set<String>,read:(String)->String?) {
        database.withTransaction {
            val dao=database.dao()
            if(dao.vault(MARKER)!=null)return@withTransaction
            for(name in keys.sorted()) {
                require(name!=MARKER) { "Legacy marker name is reserved" }
                val value=read(name) ?: error("Legacy entry missing; original retained")
                dao.putVault(VaultEntry(name,value))
            }
            dao.putVault(VaultEntry(MARKER,"complete"))
        }
    }
}
