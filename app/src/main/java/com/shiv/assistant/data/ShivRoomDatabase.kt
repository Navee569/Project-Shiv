package com.shiv.assistant.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Entity(tableName = "secure_entries")
data class SecureEntry(@PrimaryKey val name: String, val value: String)

@Dao
interface SecureEntries {
    @Query("SELECT value FROM secure_entries WHERE name = :key LIMIT 1")
    fun read(key: String): String?

    @Query("SELECT * FROM secure_entries ORDER BY name")
    fun all(): List<SecureEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun writeAll(records: List<SecureEntry>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun write(record: SecureEntry)
}

/**
 * Actual Room + SQLCipher storage. The passphrase comes from a random secret
 * wrapped by Android Keystore in the legacy encrypted preferences; no cleartext fallback.
 */
@Database(entities = [SecureEntry::class], version = 1, exportSchema = false)
abstract class ShivRoomDatabase : RoomDatabase() {
    abstract fun entries(): SecureEntries

    companion object {
        @Volatile private var instance: ShivRoomDatabase? = null

        fun open(context: Context, password: ByteArray): ShivRoomDatabase {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: run {
                    System.loadLibrary("sqlcipher")
                    val factory = SupportOpenHelperFactory(password)
                    val db = Room.databaseBuilder(
                        context.applicationContext,
                        ShivRoomDatabase::class.java,
                        "shiv-personal-v1.db"
                    ).openHelperFactory(factory)
                        // Existing controller uses synchronous reads. Move DB IO to
                        // suspend repositories before production; never pretend ready.
                        .allowMainThreadQueries()
                        .build()
                    instance = db
                    db
                }
            }
        }
    }
}
