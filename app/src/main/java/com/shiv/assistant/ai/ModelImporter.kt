package com.shiv.assistant.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.shiv.assistant.data.EncryptedDatabase
import com.shiv.assistant.data.ModelRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

/** LiteRT requires an absolute file path. SAF content providers do not supply one. */
data class ModelImportProgress(val bytesCopied: Long, val totalBytes: Long, val copied: Boolean)
class ModelImporter(private val context: Context) {
    /**
     * Model source stays owner-selected; app-private copy is required by the runtime.
     * Caller must show the free space and confirm before invoking this function.
     */
    suspend fun import(uri: Uri, role: String, report: (ModelImportProgress) -> Unit): ModelRecord =
        withContext(Dispatchers.IO) {
            require(role in setOf("router", "conversation"))
            val cr = context.contentResolver
            var name = "model.litertlm"
            var size = -1L
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = cursor.getString(it) }
                    cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { if (!cursor.isNull(it)) size = cursor.getLong(it) }
                }
            }
            require(name.endsWith(".litertlm", ignoreCase=true)) { "Choose a .litertlm model" }
            require(size in 4096..6_000_000_000L) { "Invalid or unsupported model size" }
            require(context.filesDir.usableSpace > size + 200_000_000L) {
                "Not enough app-private storage for a full model copy. Free space first."
            }
            val directory = File(context.filesDir, "Shiv AI Models").apply { mkdirs() }
            val id = UUID.randomUUID().toString()
            val temp = File(directory, "$id.partial")
            val dest = File(directory, "$id.litertlm")
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            try {
                val stream = cr.openInputStream(uri) ?: error("Cannot read selected model")
                stream.use { input -> FileOutputStream(temp).use { out ->
                    val buf = ByteArray(128 * 1024)
                    while (true) {
                        val count = input.read(buf)
                        if (count < 0) break
                        digest.update(buf,0,count)
                        out.write(buf,0,count)
                        copied += count
                        if (copied % (4L*1024*1024) < 128*1024) report(ModelImportProgress(copied,size,true))
                    }
                    out.fd.sync()
                } }
                require(copied == size) { "Copied bytes differ from file metadata" }
                // Never rely on the filename alone; content integrity is SHA-256 recorded.
                require(temp.renameTo(dest)) { "Failed to finalize imported model" }
                val sha = digest.digest().joinToString("") { "%02x".format(it) }
                ModelRecord(id,name,role,dest.absolutePath,sha,copied,"litertlm").also {
                    EncryptedDatabase.open(context).dao().putModel(it)
                }
            } finally {
                temp.delete()
            }
        }
    suspend fun remove(model: ModelRecord) = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "Shiv AI Models").canonicalFile
        val file = File(model.uri).canonicalFile
        require(file.parentFile == dir && file.name.endsWith(".litertlm")) { "Not an imported Shiv model" }
        check(!file.exists() || file.delete()) { "Unable to remove model file" }
        EncryptedDatabase.open(context).dao().deleteModel(model.id)
    }
}

/** A file's display name is untrusted. Recheck bytes and hash at load time. */
object ModelIntegrity {
    fun valid(file:File,expectedBytes:Long,expectedSha256:String):Boolean {
        if(!file.isFile || file.length()!=expectedBytes || expectedBytes<4096) return false
        if(!Regex("[0-9a-f]{64}").matches(expectedSha256))return false
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer=ByteArray(256*1024)
            while(true) {
                val read=input.read(buffer)
                if(read==-1)break
                digest.update(buffer,0,read)
            }
        }
        return digest.digest().joinToString(""){"%02x".format(it)}==expectedSha256
    }
}
