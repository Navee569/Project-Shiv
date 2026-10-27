package com.shiv.assistant.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    private const val DB_NAME="shiv-private-v1.db"
    val MIGRATION_1_2=object:Migration(1,2) {
        override fun migrate(db:SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_action_log_type` ON `action_log` (`type`)")
        }
    }

    fun open(context: Context): ShivDatabase = instance ?: synchronized(this) {
        instance ?: create(context.applicationContext).also { instance = it }
    }
    /** Force SQLCipher open, Room migration, and an integrity check before showing private UI. */
    fun verify(context:Context) {
        try {
            checkIntegrity(open(context))
        } catch (failure:Exception) {
            closeHandle()
            throw IllegalStateException("Encrypted Shiv storage cannot be opened safely",failure)
        }
    }
    fun checkIntegrity(database:ShivDatabase) {
        database.openHelper.writableDatabase.query("PRAGMA quick_check").use { cursor ->
            check(cursor.moveToFirst() && cursor.getString(0)=="ok") {
                "Encrypted database integrity check failed"
            }
        }
    }
    private fun closeHandle() = synchronized(this) {
        runCatching { instance?.close() }
        instance=null
    }
    /** Irreversible owner-driven recovery for a lost key or damaged ciphertext. */
    suspend fun resetAfterOwnerConfirmation(context:Context,phrase:String) = withContext(Dispatchers.IO) {
        require(phrase=="ERASE SHIV STORAGE") { "Owner confirmation is required" }
        val app=context.applicationContext
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        closeHandle()
        val dbFile=app.getDatabasePath(DB_NAME)
        if(dbFile.exists()) check(app.deleteDatabase(DB_NAME)) { "Encrypted database could not be removed" }
        check(app.getSharedPreferences(PREF_FILE,Context.MODE_PRIVATE).edit().clear().commit())
        check(app.getSharedPreferences("shiv_encrypted_v1",Context.MODE_PRIVATE).edit().clear().commit())
        for(name in listOf("shiv_awareness_control","shiv_call_check_in","shiv_learning"))
            check(app.getSharedPreferences(name,Context.MODE_PRIVATE).edit().clear().commit())
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        for(alias in listOf(KEY_ALIAS,"shiv_vault_master_v1"))
            if(store.containsAlias(alias))store.deleteEntry(alias)
    }
    private fun create(context: Context): ShivDatabase {
        val passphrase = getOrCreatePassphrase(context)
        System.loadLibrary("sqlcipher")
        val factory = SupportOpenHelperFactory(passphrase)
        return Room.databaseBuilder(context, ShivDatabase::class.java, DB_NAME)
            .openHelperFactory(factory)
            .addMigrations(MIGRATION_1_2)
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
        if (context.getDatabasePath(DB_NAME).exists()) {
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
