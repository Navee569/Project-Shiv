package com.shiv.assistant

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shiv.assistant.data.EncryptedDatabase
import com.shiv.assistant.data.ShivDatabase
import com.shiv.assistant.data.VaultEntry
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Builds an actual encrypted v1 database from the committed v1 Room schema. */
@RunWith(AndroidJUnit4::class)
class EncryptedSchemaMigrationInstrumentedTest {
    @Test fun encryptedVersionOneReopensAtVersionTwoWithoutLosingRecords() = runBlocking {
        System.loadLibrary("sqlcipher")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val testAssets=InstrumentationRegistry.getInstrumentation().context.assets
        val name="schema-migration-${UUID.randomUUID()}.db"
        val key=ByteArray(32) { (it+1).toByte() }
        val schema=JSONObject(testAssets.open(
            "com.shiv.assistant.data.ShivDatabase/1.json").bufferedReader().use { it.readText() })
            .getJSONObject("database")
        val factory=SupportOpenHelperFactory(key.copyOf())
        val helper=factory.create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object:SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db:androidx.sqlite.db.SupportSQLiteDatabase) {
                    val entities=schema.getJSONArray("entities")
                    for(i in 0 until entities.length()) {
                        val entity=entities.getJSONObject(i)
                        val table=entity.getString("tableName")
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
                        val indices=entity.optJSONArray("indices") ?: continue
                        for(j in 0 until indices.length())
                            db.execSQL(indices.getJSONObject(j).getString("createSql")
                                .replace("\${TABLE_NAME}",table))
                    }
                    val setup=schema.getJSONArray("setupQueries")
                    for(i in 0 until setup.length())db.execSQL(setup.getString(i))
                }
                override fun onUpgrade(db:androidx.sqlite.db.SupportSQLiteDatabase,oldVersion:Int,newVersion:Int) {}
            }).build())
        try {
            val old=helper.writableDatabase
            old.execSQL("INSERT INTO vault_entries(name,value) VALUES('migration-proof','private-marker')")
            helper.close()
            val db=Room.databaseBuilder(context,ShivDatabase::class.java,name)
                .openHelperFactory(SupportOpenHelperFactory(key.copyOf()))
                .addMigrations(EncryptedDatabase.MIGRATION_1_2).build()
            try {
                assertEquals("private-marker",db.dao().vault("migration-proof")?.value)
                db.dao().putVault(VaultEntry("new-write","survives"))
                assertEquals(2,db.openHelper.writableDatabase.version)
            } finally { db.close() }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }
}
