package com.shiv.assistant.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.Room
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Fail-closed SQLCipher bootstrap. DB passphrase is random, never compiled in or
 * stored unwrapped; only the Android Keystore key can unwrap it.
 * A missing key with existing ciphertext is an unrecoverable explicit error.
 */
object EncryptedDatabase {
    @Volatile private var instance: ShivDatabase? = null
    private const val KEY_ALIAS = "shiv_database_wrap_v1"
    private const val PREF_FILE = "shiv_db_key_envelope"
    private const val WRAPPED = "wrapped_passphrase"

    fun open(context: Context): ShivDatabase = instance ?: synchronized(this) {
        instance ?: create(context.applicationContext).also { instance = it }
    }
    private fun create(context: Context): ShivDatabase {
        val passphrase = getOrCreatePassphrase(context)
        System.loadLibrary("sqlcipher")
        val factory = SupportOpenHelperFactory(passphrase)
        return Room.databaseBuilder(context, ShivDatabase::class.java, "shiv-private-v1.db")
            .openHelperFactory(factory)
            .build()
    }
    private fun getOrCreatePassphrase(context: Context): ByteArray {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(KEY_ALIAS, null) as? SecretKey
        val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        val envelope = prefs.getString(WRAPPED, null)
        if (envelope != null) {
            val key = existing ?: error("Shiv encryption key lost. Encrypted records cannot be opened.")
            val blob = Base64.decode(envelope, Base64.NO_WRAP)
            require(blob.size >= 12 + 16 + 32) { "Invalid database key envelope" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob.copyOfRange(0,12)))
            return cipher.doFinal(blob.copyOfRange(12, blob.size))
        }
        // No silent recreation if a database already exists.
        if (context.getDatabasePath("shiv-private-v1.db").exists()) {
            error("Existing Shiv database has no key envelope. Restore the original key.")
        }
        val key = existing ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        ).run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
            generateKey()
        }
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val wrapped = cipher.iv + cipher.doFinal(raw)
        check(prefs.edit().putString(WRAPPED, Base64.encodeToString(wrapped, Base64.NO_WRAP)).commit()) {
            "Unable to persist database key envelope"
        }
        return raw
    }
}
