package app.shiv

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/** Test APK only: a destination that opens but refuses writes, with observable cleanup. */
class FailingBackupProvider : ContentProvider() {
    private val output get() = File(requireNotNull(context).cacheDir, "failed-provider-backup")
    override fun onCreate() = true
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        output.writeText("incomplete output")
        return ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        if (output.delete()) 1 else 0
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
        MatrixCursor(arrayOf("exists")).apply { addRow(arrayOf(if (output.exists()) 1 else 0)) }
    override fun getType(uri: Uri) = "application/octet-stream"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
