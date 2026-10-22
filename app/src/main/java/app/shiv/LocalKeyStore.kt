package app.shiv

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/** Wraps the database key with a non-exportable Android Keystore AES key. */
class LocalKeyStore(private val context: Context) {
    fun databaseKey(): ByteArray {
        val prefs = context.getSharedPreferences("shiv_secure", Context.MODE_PRIVATE)
        val wrapped = prefs.getString("wrapped_db_key", null)
        if (wrapped != null) {
            val packed = try { Base64.decode(wrapped, Base64.NO_WRAP) }
            catch (failure: IllegalArgumentException) { throw DatabaseKeyRecoveryException("The wrapped database key is corrupt; encrypted data was preserved.", failure) }
            val wrappingKey = getExistingWrappingKey()
                ?: throw DatabaseKeyRecoveryException("The Android Keystore key for Shiv's existing database is missing. The encrypted database was preserved; restore from a valid encrypted backup if available.")
            return try {
                require(packed.size > 12) { "Wrapped database key is truncated" }
                cipher(Cipher.DECRYPT_MODE, wrappingKey, packed.copyOfRange(0, 12)).doFinal(packed.copyOfRange(12, packed.size))
            } catch (failure: Exception) {
                throw DatabaseKeyRecoveryException("Shiv could not unlock its existing database key. The encrypted database was preserved; restore from a valid encrypted backup if available.", failure)
            }
        }
        if (databaseKeyState(
                hasWrappedMetadata = false,
                databaseFileExists = context.getDatabasePath("shiv-memory.db").exists()
            ) == DatabaseKeyState.MISSING_METADATA_WITH_DATABASE
        ) {
            throw DatabaseKeyRecoveryException(
                "Shiv found an existing encrypted database but its wrapped key metadata is missing. " +
                    "The database was preserved; restore from a valid encrypted backup if available."
            )
        }
        val raw = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = cipher(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey(), null)
        val packed = cipher.iv + cipher.doFinal(raw)
        check(prefs.edit().putString("wrapped_db_key", Base64.encodeToString(packed, Base64.NO_WRAP)).commit()) { "Could not store database key" }
        return raw
    }

    private fun cipher(mode: Int, key: SecretKey, iv: ByteArray?): Cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
        if (mode == Cipher.ENCRYPT_MODE) init(mode, key) else init(mode, key, GCMParameterSpec(128, requireNotNull(iv)))
    }

    private fun getExistingWrappingKey(): SecretKey? {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return store.getKey(ALIAS, null) as? SecretKey
    }
    private fun getOrCreateWrappingKey(): SecretKey {
        getExistingWrappingKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    private companion object { const val ALIAS = "shiv_database_wrapping_key" }
}


/** Typed failure that prevents replacing or deleting an existing encrypted database. */
class DatabaseKeyRecoveryException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
