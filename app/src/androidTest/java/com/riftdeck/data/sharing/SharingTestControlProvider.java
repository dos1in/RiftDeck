package com.riftdeck.data.sharing;

import android.content.ContentProvider;
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Binder;
import android.os.RemoteException;

/** Fixture-only controls bootstrap the protected document provider from its own test APK UID. */
public final class SharingTestControlProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        long token = Binder.clearCallingIdentity();
        try (ContentProviderClient client = getContext().getContentResolver().acquireContentProviderClient(SharingTestDocumentsProvider.AUTHORITY)) {
            return client.call("test:" + method, arg, extras);
        } catch (RemoteException error) { throw new IllegalStateException(error); }
        finally { Binder.restoreCallingIdentity(token); }
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
