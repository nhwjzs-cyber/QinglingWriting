package com.qingling.writing;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Test APK process deliberately uses only platform Java APIs (no target APK classpath). */
public class BackupTestDocumentsProvider extends DocumentsProvider {
    public static final String AUTHORITY = "com.qingling.writing.test.backupdocs";
    public static final Uri TREE = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "root");
    private File root() { File r = new File(getContext().getFilesDir(), "backup-test-documents"); r.mkdirs(); return r; }
    private File file(String id) throws FileNotFoundException {
        try {
            File f = new File(root(), id).getCanonicalFile();
            if (!root().getCanonicalFile().equals(f.getParentFile())) throw new FileNotFoundException("Outside test directory");
            return f;
        } catch (IOException e) { throw new FileNotFoundException(e.toString()); }
    }
    @Override public boolean onCreate() { return true; }
    @Override public Cursor queryRoots(String[] projection) {
        String[] cols = projection != null ? projection : new String[]{"root_id", "document_id", "title", "flags", "mime_types", "available_bytes"};
        MatrixCursor result = new MatrixCursor(cols); MatrixCursor.RowBuilder row = result.newRow();
        for (String c : cols) {
            Object value = null;
            if (c.equals("root_id") || c.equals("document_id")) value = "root";
            if (c.equals("title")) value = "自动备份测试目录";
            if (c.equals("flags")) value = DocumentsContract.Root.FLAG_SUPPORTS_CREATE;
            if (c.equals("mime_types")) value = "*/*";
            if (c.equals("available_bytes")) value = root().getUsableSpace();
            row.add(c, value);
        }
        return result;
    }
    private String[] columns(String[] projection) { return projection != null ? projection : new String[]{"document_id", "_display_name", "mime_type", "flags", "_size"}; }
    private void add(MatrixCursor cursor, String id) throws FileNotFoundException {
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String c : cursor.getColumnNames()) {
            Object value = null;
            if (c.equals("document_id")) value = id;
            if (c.equals("_display_name")) value = id.equals("root") ? "自动备份测试目录" : id;
            if (c.equals("mime_type")) value = id.equals("root") ? DocumentsContract.Document.MIME_TYPE_DIR : "application/zip";
            if (c.equals("flags")) value = id.equals("root") ? DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE : DocumentsContract.Document.FLAG_SUPPORTS_WRITE | DocumentsContract.Document.FLAG_SUPPORTS_DELETE;
            if (c.equals("_size")) value = id.equals("root") ? 0L : file(id).length();
            row.add(c, value);
        }
    }
    @Override public Cursor queryDocument(String id, String[] projection) throws FileNotFoundException {
        MatrixCursor result = new MatrixCursor(columns(projection)); add(result, id); return result;
    }
    @Override public Cursor queryChildDocuments(String parent, String[] projection, String sort) throws FileNotFoundException {
        MatrixCursor result = new MatrixCursor(columns(projection)); File[] files = root().listFiles();
        if (files != null) for (File f : files) add(result, f.getName());
        return result;
    }
    @Override public String createDocument(String parent, String mime, String name) throws FileNotFoundException {
        if (!parent.equals("root")) throw new FileNotFoundException("Wrong parent");
        File target = file(name);
        try { if (!target.createNewFile()) throw new FileNotFoundException("Already exists"); }
        catch (IOException e) { throw new FileNotFoundException(e.toString()); }
        return target.getName();
    }
    @Override public void deleteDocument(String id) throws FileNotFoundException { if (!file(id).delete()) throw new FileNotFoundException("Cannot delete"); }
    @Override public boolean isChildDocument(String parent, String id) { return parent.equals("root") && !id.equals("root"); }
    @Override public ParcelFileDescriptor openDocument(String id, String mode, CancellationSignal signal) throws FileNotFoundException {
        if (mode.equals("r") && new File(getContext().getFilesDir(), "fail-backup-read").exists()) throw new FileNotFoundException("Test verification failure");
        return ParcelFileDescriptor.open(file(id), ParcelFileDescriptor.parseMode(mode));
    }
}
