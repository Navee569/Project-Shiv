package com.shiv.assistant.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** PBKDF2-HMAC-SHA256 with authenticated AES-256-GCM. Never export cleartext. */
object BackupCrypto {
    private val rng = SecureRandom()
    fun seal(raw: ByteArray, password: CharArray): String {
        require(password.size >= 12) { "Use a backup passphrase of at least 12 characters" }
        val salt = ByteArray(16).also(rng::nextBytes)
        val iv = ByteArray(12).also(rng::nextBytes)
        val key = derive(password, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            "SHIV1." + Base64.getEncoder().encodeToString(salt + iv + cipher.doFinal(raw))
        } finally { key.fill(0) }
    }
    fun open(blob: String, password: CharArray): ByteArray {
        require(password.size >= 12)
        require(blob.startsWith("SHIV1.")) { "Unrecognized backup format" }
        val data = Base64.getDecoder().decode(blob.removePrefix("SHIV1."))
        require(data.size >= 16 + 12 + 16) { "Backup truncated" }
        val salt = data.copyOfRange(0, 16)
        val iv = data.copyOfRange(16, 28)
        val key = derive(password, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.doFinal(data.copyOfRange(28, data.size))
        } finally { key.fill(0) }
    }
    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, 250_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
}
/**
 * Encrypted user-selected export/merge restore. Never copies model weights,
 * and never sends data to network or provider endpoints.
 */
class ShivEncryptedBackup(private val context: Context, private val vault: ShivVault) {
    suspend fun export(destination: Uri, passphrase: String): Int = withContext(Dispatchers.IO) {
        val rows = vault.backupRecords()
        val data = JSONArray()
        rows.forEach { row -> data.put(JSONObject().put("name", row.name).put("value", row.value)) }
        val plain = JSONObject().put("schema", 1).put("records", data).toString().toByteArray()
        val cipherText = BackupCrypto.seal(plain, passphrase.toCharArray())
        context.contentResolver.openOutputStream(destination, "w")?.use {
            it.write(cipherText.toByteArray(Charsets.UTF_8))
        } ?: error("Cannot write backup")
        plain.fill(0)
        rows.size
    }
    suspend fun restore(source: Uri, passphrase: String): Int = withContext(Dispatchers.IO) {
        val encoded = context.contentResolver.openInputStream(source)?.use { stream ->
            val bytes = stream.readBytes()
            require(bytes.size <= 10_000_000) { "Backup exceeds 10 MB limit" }
            String(bytes, Charsets.UTF_8)
        } ?: error("Cannot read backup")
        val plain = BackupCrypto.open(encoded, passphrase.toCharArray())
        try {
            val obj = JSONObject(String(plain, Charsets.UTF_8))
            require(obj.getInt("schema") == 1) { "Unsupported backup version" }
            val items = obj.getJSONArray("records")
            require(items.length() <= 1000) { "Too many entries" }
            val validated = (0 until items.length()).map { i ->
                val item = items.getJSONObject(i)
                val name = item.getString("name")
                val value = item.getString("value")
                require(name.matches(Regex("[a-zA-Z0-9_]{1,100}"))) { "Unsafe record name" }
                require(value.length <= 2_000_000) { "Oversized record" }
                SecureEntry(name, value)
            }
            // Room bulk insert is transactional; existing records not included in
            // this backup are not deleted. Owner can choose explicit deletion later.
            vault.mergeBackupRecords(validated)
            validated.size
        } finally { plain.fill(0) }
    }
}
