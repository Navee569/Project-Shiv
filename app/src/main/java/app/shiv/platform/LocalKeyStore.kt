package app.shiv.platform

import app.shiv.*

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

class DatabaseKeyUnavailableException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

internal interface DatabaseWrappingKeyProvider {
    fun existingKey(): SecretKey?
    fun createKey(): SecretKey
}

/** Wraps the database key with a non-exportable Android Keystore AES key. */
class LocalKeyStore internal constructor(
    private val context: Context,
    private val wrappingKeys: DatabaseWrappingKeyProvider,
    private val databaseName: String = "shiv-memory.db"
) {
    constructor(context: Context) : this(context, AndroidDatabaseWrappingKeyProvider(), "shiv-memory.db")
    internal constructor(context: Context, databaseName: String) : this(context, AndroidDatabaseWrappingKeyProvider(), databaseName)

    fun databaseKey(): ByteArray {
        val prefs = context.getSharedPreferences("shiv_secure", Context.MODE_PRIVATE)
        val wrapped = prefs.getString("wrapped_db_key", null)
        if (wrapped != null) {
            val key = wrappingKeys.existingKey()
                ?: throw DatabaseKeyUnavailableException("The Android Keystore key for Shiv's encrypted database is missing. The database was left unchanged.")
            return try {
                val packed = Base64.decode(wrapped, Base64.NO_WRAP)
                require(packed.size > 12) { "Wrapped key record is incomplete" }
                cipher(Cipher.DECRYPT_MODE, key, packed.copyOfRange(0, 12)).doFinal(packed.copyOfRange(12, packed.size))
            } catch (failure: Exception) {
                throw DatabaseKeyUnavailableException("Shiv could not unlock its encrypted database. No plaintext database was opened.", failure)
            }
        }
        if (context.getDatabasePath(databaseName).exists()) {
            throw DatabaseKeyUnavailableException("The wrapped key for Shiv's existing encrypted database is missing. The database was left unchanged.")
        }
        val raw = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val key = wrappingKeys.existingKey() ?: wrappingKeys.createKey()
        val cipher = cipher(Cipher.ENCRYPT_MODE, key, null)
        val packed = cipher.iv + cipher.doFinal(raw)
        check(prefs.edit().putString("wrapped_db_key", Base64.encodeToString(packed, Base64.NO_WRAP)).commit()) { "Could not store database key" }
        return raw
    }

    private fun cipher(mode: Int, key: SecretKey, iv: ByteArray?): Cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
        if (mode == Cipher.ENCRYPT_MODE) init(mode, key) else init(mode, key, GCMParameterSpec(128, requireNotNull(iv)))
    }

    private class AndroidDatabaseWrappingKeyProvider : DatabaseWrappingKeyProvider {
      override fun existingKey(): SecretKey? {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return store.getKey(ALIAS, null) as? SecretKey
      }

      override fun createKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
      }

      private companion object { const val ALIAS = "shiv_database_wrapping_key" }
    }
}
