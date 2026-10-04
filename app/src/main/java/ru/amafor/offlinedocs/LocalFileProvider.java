package ru.amafor.offlinedocs;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;

public class LocalFileProvider extends ContentProvider {
    @Override public boolean onCreate(){ return true; }

    private File resolve(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null) throw new FileNotFoundException();
        File base = new File(getContext().getFilesDir(), "docs");
        File f = new File(base, name);
        try {
            String bp = base.getCanonicalPath() + File.separator;
            String fp = f.getCanonicalPath();
            if (!fp.startsWith(bp) || !f.isFile()) throw new FileNotFoundException();
            return f;
        } catch (Exception e) {
            throw new FileNotFoundException();
        }
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) {
        String n = uri.getLastPathSegment();
        if (n == null) return "application/octet-stream";
        int dot = n.lastIndexOf('.');
        if (dot < 0) return "application/octet-stream";
        String ext = n.substring(dot+1).toLowerCase();
        String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return m == null ? "application/octet-stream" : m;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        try {
            File f=resolve(uri);
            MatrixCursor c=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
            c.addRow(new Object[]{f.getName(), f.length()});
            return c;
        } catch(Exception e){ return null; }
    }

    @Override public Uri insert(Uri uri, ContentValues values){ throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs){ throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs){ throw new UnsupportedOperationException(); }
}
