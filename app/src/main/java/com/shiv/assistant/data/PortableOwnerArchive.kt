package com.shiv.assistant.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.io.ByteArrayOutputStream

/**
 * Owner-initiated passphrase-encrypted archive for personal SQLCipher records.
 *
 * Import is schema-checked and all-or-nothing. Imported queue work, reminders
 * and old approvals are disabled to avoid unexpected actions after a restore.
 * Model binaries and paths are device-specific and NEVER exported or overwritten.
 */
class PortableOwnerArchive(
    private val context: Context,
    private val database: ShivDatabase = EncryptedDatabase.open(context)
) {
    private val tables = listOf("vault_entries", "profiles", "memories", "events", "tasks",
        "reminders", "saved_rules", "chat_history", "action_log", "permission_tiers",
        "signals", "beliefs", "decision_traces")
    private val maxRows=50_000
    private val maxBytes=PortableBackupCodec.MAX_ARCHIVE_BYTES

    suspend fun exportEncrypted(passphrase:CharArray):ByteArray = withContext(Dispatchers.IO) {
        try {
            val json=database.runInTransaction<JSONObject> {
                val sql=database.openHelper.writableDatabase
                val data=JSONObject()
                for(table in tables) data.put(table, dumpTable(sql,table))
                JSONObject().put("version",1)
                    .put("createdAt",System.currentTimeMillis())
                    .put("contents",data)
                    .put("modelFilesExcluded",true)
            }.toString().toByteArray(StandardCharsets.UTF_8)
            try { PortableBackupCodec.encrypt(json,passphrase) }
            finally { json.fill(0) }
        } finally { passphrase.fill('\u0000') }
    }

    suspend fun exportTo(uri:Uri, passphrase:CharArray):Int = withContext(Dispatchers.IO) {
        val bytes=exportEncrypted(passphrase)
        // The SAF destination contains ONLY encrypted bytes, never JSON plaintext.
        val stream=context.contentResolver.openOutputStream(uri,"wt")
            ?: throw IllegalArgumentException("Cannot write the selected backup document")
        stream.use { it.write(bytes); it.flush() }
        bytes.size
    }

    /**
     * Validate/decrypt outside the transaction. All table/column/row validations
     * occur BEFORE the first DELETE. If any SQL insertion fails, Room rolls back.
     */
    suspend fun restoreFrom(uri:Uri, passphrase:CharArray):Int = withContext(Dispatchers.IO) {
        val encrypted=context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer=ByteArray(64*1024)
            val result=ByteArrayOutputStream()
            while(true) {
                val read=input.read(buffer)
                if(read<0)break
                result.write(buffer,0,read)
                require(result.size()<=maxBytes+64) { "Backup file exceeds 12 MB limit" }
            }
            result.toByteArray()
        } ?: throw IllegalArgumentException("Backup document is unavailable")
        restoreEncrypted(encrypted,passphrase)
    }

    suspend fun restoreEncrypted(encrypted:ByteArray,passphrase:CharArray):Int =
        withContext(Dispatchers.IO) {
        val plaintext=try { PortableBackupCodec.decrypt(encrypted,passphrase) }
            finally { passphrase.fill('\u0000') }
        val snapshot=try {
            val parsed=JSONObject(String(plaintext,StandardCharsets.UTF_8))
            require(parsed.getInt("version")==1) { "Unsupported backup version" }
            require(parsed.optBoolean("modelFilesExcluded",false)) { "Unsupported model archive" }
            val contents=parsed.getJSONObject("contents")
            require(contents.length()==tables.size) { "Unexpected backup tables" }
            val validated=linkedMapOf<String,List<ContentValues>>()
            val sql=database.openHelper.writableDatabase
            var total=0
            for(table in tables) {
                require(contents.has(table)) { "Missing archive section" }
                val columns=tableColumns(sql,table)
                val entries=contents.getJSONArray(table)
                total += entries.length()
                require(total<=maxRows) { "Backup has too many records" }
                val rows=ArrayList<ContentValues>(entries.length())
                for(i in 0 until entries.length()) {
                    val row=entries.getJSONObject(i)
                    require(row.length()==columns.size && columns.all{row.has(it)}) {
                        "Backup column layout does not match this app version"
                    }
                    val values=ContentValues()
                    for(column in columns) {
                        val value=row.get(column)
                        when(value) {
                            JSONObject.NULL -> values.putNull(column)
                            is Int -> values.put(column,value.toLong())
                            is Long -> values.put(column,value)
                            is Double -> {
                                require(value.isFinite()) { "Non-finite backup number" }
                                values.put(column,value)
                            }
                            is Boolean -> values.put(column,if(value) 1L else 0L)
                            is String -> {
                                require(value.length<=256_000) { "Archive field too large" }
                                values.put(column,value)
                            }
                            else -> throw IllegalArgumentException("Unsupported backup field type")
                        }
                    }
                    // Old reminders cannot trigger without being scheduled anew.
                    if(table=="reminders") values.put("status","RESTORED_INACTIVE")
                    if(table=="signals") {
                        values.put("status","PROCESSED")
                        values.put("processedAt",System.currentTimeMillis())
                        values.put("lastError","Restored; explicit new owner request required")
                    }
                    if(table=="decision_traces" && values.getAsString("decision")=="ASK")
                        values.put("decision","ARCHIVED")
                    if(table=="permission_tiers")values.put("tier","ASK_FIRST")
                    if(table=="vault_entries") {
                        val name=values.getAsString("name")
                        if(name=="cloud_consent")values.put("value","no")
                        if(name=="brain_mode")values.put("value","LOCAL")
                        if(name=="post_call_opt_in" || name=="notification_opt_in" ||
                            name=="screen_allowed_package")values.put("value","")
                    }
                    rows.add(values)
                }
                validated[table]=rows
            }
            validated
        } finally { plaintext.fill(0) }

        // The currently running WorkManager jobs remain owned by the previous
        // database state; obtain their IDs before replacing its private records.
        val oldReminderIds=database.dao().reminderIds()
        database.runInTransaction {
            val sql=database.openHelper.writableDatabase
            for(table in tables.asReversed())sql.execSQL("DELETE FROM "+table)
            for(table in tables)for(row in snapshot.getValue(table)) {
                val result=sql.insert(table,android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,row)
                check(result>=0) { "Backup restore rejected by encrypted database" }
            }
        }
        // Invalidate previous background delivery after commit. Restored
        // reminders and approvals remain inert by design.
        val work=androidx.work.WorkManager.getInstance(context)
        for(id in oldReminderIds)work.cancelAllWorkByTag("shiv-reminder-"+id)
        work.cancelUniqueWork("shiv-durable-signals")
        work.cancelUniqueWork("shiv-opt-in-pattern-learning")
        val prefs=context.getSharedPreferences("shiv_awareness_control",Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        context.getSharedPreferences("shiv_learning",Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("shiv_call_check_in",Context.MODE_PRIVATE).edit().clear().commit()
        snapshot.values.sumOf { it.size }
    }

    private fun dumpTable(db:SupportSQLiteDatabase,table:String):JSONArray {
        val output=JSONArray()
        db.query("SELECT * FROM "+table).use { cursor ->
            while(cursor.moveToNext()) {
                require(output.length()<maxRows) { "Backup has too many records" }
                val row=JSONObject()
                for(i in 0 until cursor.columnCount) {
                    val name=cursor.getColumnName(i)
                    when(cursor.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> row.put(name,JSONObject.NULL)
                        Cursor.FIELD_TYPE_INTEGER -> row.put(name,cursor.getLong(i))
                        Cursor.FIELD_TYPE_FLOAT -> row.put(name,cursor.getDouble(i))
                        Cursor.FIELD_TYPE_STRING -> row.put(name,cursor.getString(i))
                        else -> throw IllegalStateException("Unsupported binary database column: "+name)
                    }
                }
                output.put(row)
            }
        }
        return output
    }

    private fun tableColumns(db:SupportSQLiteDatabase,table:String):Set<String> {
        val result=linkedSetOf<String>()
        db.query("PRAGMA table_info("+table+")").use { cursor ->
            val idx=cursor.getColumnIndexOrThrow("name")
            while(cursor.moveToNext())result.add(cursor.getString(idx))
        }
        require(result.isNotEmpty())
        return result
    }
}
