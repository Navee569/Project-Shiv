package app.shiv;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Test APK only. Its standalone provider process must not require target-app Kotlin classes. */
public final class FailingBackupProvider extends ContentProvider {
    private File output() {
        return new File(getContext().getCacheDir(), "failed-provider-backup");
    }

    @Override public boolean onCreate() { return true; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File destination = output();
        try (FileOutputStream stream = new FileOutputStream(destination)) {
            stream.write("incomplete output".getBytes(StandardCharsets.UTF_8));
        } catch (IOException failure) {
            FileNotFoundException error = new FileNotFoundException("Could not prepare test destination");
            error.initCause(failure);
            throw error;
        }
        return ParcelFileDescriptor.open(destination, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        return output().delete() ? 1 : 0;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(new String[]{"exists"});
        cursor.addRow(new Object[]{output().exists() ? 1 : 0});
        return cursor;
    }

    @Override public String getType(Uri uri) { return "application/octet-stream"; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
