package com.riftdeck.data.sharing;

import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/** The standalone test APK needs no target-app Kotlin runtime to expose private fixture documents. */
public final class SharingTestDocumentsProvider extends DocumentsProvider {
    public static final String AUTHORITY = "com.riftdeck.tests.sharing.documents";
    public static final String ROOT_ID = "fixture-root";
    private static final String[] COLUMNS = {Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS};
    private boolean failIncomingRename, failRestoreRename, failHiddenDelete, unavailable;
    private int reads;

    public static Uri treeUri() { return DocumentsContract.buildTreeDocumentUri(AUTHORITY, ROOT_ID); }
    private File root() { return new File(getContext().getFilesDir(), "sharing-provider-fixtures"); }

    @Override public boolean onCreate() {
        root().mkdirs();
        grant(treeUri());
        return true;
    }

    private void grant(Uri uri) {
        getContext().grantUriPermission("com.riftdeck", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (!method.startsWith("test:")) return super.call(method, arg, extras);
        if (Binder.getCallingUid() != Process.myUid()) throw new SecurityException("Fixture controls are private");
        try {
            switch (method) {
                case "test:reset":
                    removeTree(root()); root().mkdirs();
                    failIncomingRename = failRestoreRename = failHiddenDelete = unavailable = false;
                    reads = 0;
                    break;
                case "test:write":
                    File output = file(extras.getString("path"));
                    output.getParentFile().mkdirs();
                    try (FileOutputStream stream = new FileOutputStream(output)) { stream.write(extras.getByteArray("bytes")); }
                    output.setLastModified(System.currentTimeMillis() - 10000);
                    break;
                case "test:remove": file(extras.getString("path")).delete(); break;
                case "test:mkdir": file(extras.getString("path")).mkdirs(); break;
                case "test:grant":
                    String path = extras.getString("path"); file(path);
                    grant(DocumentsContract.buildTreeDocumentUri(AUTHORITY, ROOT_ID + "/" + path));
                    break;
                case "test:flags":
                    failIncomingRename = extras != null && extras.getBoolean("failIncomingRename");
                    failRestoreRename = extras != null && extras.getBoolean("failRestoreRename");
                    failHiddenDelete = extras != null && extras.getBoolean("failHiddenDelete");
                    unavailable = extras != null && extras.getBoolean("unavailable");
                    break;
                case "test:stats":
                    Bundle stats = new Bundle(); stats.putInt("reads", reads);
                    ArrayList<String> files = new ArrayList<>(); collectFiles(root(), files);
                    stats.putStringArrayList("files", files); return stats;
            }
            return Bundle.EMPTY;
        } catch (IOException error) { throw new IllegalStateException(error); }
    }

    @Override public Cursor queryRoots(String[] projection) {
        String[] columns = projection != null ? projection : new String[]{Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS};
        MatrixCursor result = new MatrixCursor(columns);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            switch (columns[i]) {
                case Root.COLUMN_ROOT_ID: case Root.COLUMN_DOCUMENT_ID: row[i] = ROOT_ID; break;
                case Root.COLUMN_TITLE: row[i] = "RiftDeck test saves"; break;
                case Root.COLUMN_FLAGS: row[i] = Root.FLAG_SUPPORTS_CREATE; break;
            }
        }
        result.addRow(row); return result;
    }

    @Override public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        checkAvailable(); File target = document(documentId);
        if (!target.exists()) throw new FileNotFoundException();
        String[] columns = projection != null ? projection : COLUMNS;
        MatrixCursor result = new MatrixCursor(columns); addDocument(result, columns, target); return result;
    }

    @Override public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder) throws FileNotFoundException {
        checkAvailable(); File parent = document(parentDocumentId);
        if (!parent.isDirectory()) throw new FileNotFoundException();
        String[] columns = projection != null ? projection : COLUMNS;
        MatrixCursor result = new MatrixCursor(columns);
        File[] files = parent.listFiles();
        if (files != null) {
            Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
            for (File file : files) addDocument(result, columns, file);
        }
        return result;
    }

    @Override public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal) throws FileNotFoundException {
        checkAvailable(); if (mode.equals("r")) reads++;
        return ParcelFileDescriptor.open(document(documentId), ParcelFileDescriptor.parseMode(mode));
    }

    @Override public String createDocument(String parentDocumentId, String mimeType, String displayName) throws FileNotFoundException {
        checkAvailable();
        if (displayName.contains("/") || displayName.contains("\\")) throw new FileNotFoundException();
        File created = new File(document(parentDocumentId), displayName);
        if (created.exists()) throw new FileNotFoundException("Name collision");
        try {
            boolean success = mimeType.equals(Document.MIME_TYPE_DIR) ? created.mkdir() : created.createNewFile();
            if (!success) throw new FileNotFoundException("Cannot create");
        } catch (IOException error) { throw new FileNotFoundException(error.toString()); }
        return id(created);
    }

    @Override public String renameDocument(String documentId, String displayName) throws FileNotFoundException {
        checkAvailable(); File original = document(documentId);
        if (failIncomingRename && original.getName().startsWith(".riftdeck-incoming-") && displayName.equals("Game.sav"))
            throw new FileNotFoundException("Injected incoming rename failure");
        if (failRestoreRename && original.getName().startsWith(".riftdeck-replaced-")) throw new FileNotFoundException("Injected restoration failure");
        File renamed = new File(original.getParentFile(), displayName);
        if (renamed.exists() || !original.renameTo(renamed)) throw new FileNotFoundException("Cannot rename");
        return id(renamed); // Exercise providers whose document IDs change on rename.
    }

    @Override public void deleteDocument(String documentId) throws FileNotFoundException {
        checkAvailable(); File target = document(documentId);
        if (failHiddenDelete && target.getName().startsWith(".riftdeck-replaced-")) throw new FileNotFoundException("Injected cleanup failure");
        if (!target.delete()) throw new FileNotFoundException("Cannot delete");
    }

    @Override public boolean isChildDocument(String parentDocumentId, String documentId) { return documentId.startsWith(parentDocumentId + "/"); }
    private void checkAvailable() throws FileNotFoundException { if (unavailable) throw new FileNotFoundException("Injected disconnected storage"); }
    private File document(String id) throws FileNotFoundException { return id.equals(ROOT_ID) ? root() : file(id.substring((ROOT_ID + "/").length())); }
    private File file(String path) throws FileNotFoundException {
        File target = new File(root(), path);
        try { if (!target.getCanonicalPath().startsWith(root().getCanonicalPath() + "/")) throw new FileNotFoundException("Unsafe fixture path"); }
        catch (IOException error) { throw new FileNotFoundException(error.toString()); }
        return target;
    }
    private String id(File file) {
        if (file.equals(root())) return ROOT_ID;
        String relative = root().toURI().relativize(file.toURI()).getPath();
        return ROOT_ID + "/" + (relative.endsWith("/") ? relative.substring(0, relative.length() - 1) : relative);
    }
    private void addDocument(MatrixCursor cursor, String[] columns, File file) {
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            switch (columns[i]) {
                case Document.COLUMN_DOCUMENT_ID: row[i] = id(file); break;
                case Document.COLUMN_DISPLAY_NAME: row[i] = file.getName(); break;
                case Document.COLUMN_MIME_TYPE: row[i] = file.isDirectory() ? Document.MIME_TYPE_DIR : "application/octet-stream"; break;
                case Document.COLUMN_SIZE: row[i] = file.length(); break;
                case Document.COLUMN_LAST_MODIFIED: row[i] = file.lastModified(); break;
                case Document.COLUMN_FLAGS: row[i] = Document.FLAG_SUPPORTS_WRITE | Document.FLAG_SUPPORTS_DELETE |
                        Document.FLAG_SUPPORTS_RENAME | (file.isDirectory() ? Document.FLAG_DIR_SUPPORTS_CREATE : 0); break;
            }
        }
        cursor.addRow(row);
    }
    private void collectFiles(File directory, ArrayList<String> result) {
        File[] children = directory.listFiles(); if (children == null) return;
        for (File child : children) if (child.isDirectory()) collectFiles(child, result); else result.add(root().toURI().relativize(child.toURI()).getPath());
    }
    private static void removeTree(File file) {
        File[] children = file.listFiles(); if (children != null) for (File child : children) removeTree(child);
        file.delete();
    }
}
