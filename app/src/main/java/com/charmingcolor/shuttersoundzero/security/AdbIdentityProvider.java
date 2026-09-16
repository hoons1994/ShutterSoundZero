// SPDX-License-Identifier: GPL-3.0-or-later
package com.charmingcolor.shuttersoundzero.security;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import io.github.muntashirakon.adb.AdbShellIdentity;

/**
 * The framework also protects this provider with android.permission.DUMP. The explicit kernel-
 * supplied Binder UID check below is mandatory: holding DUMP alone is not proof of shell identity.
 * This provider never reads/writes settings, grants permissions, returns app data or runs commands.
 */
public final class AdbIdentityProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        int uid = Binder.getCallingUid(); // Capture before any identity clearing; ignore extras.
        if (uid != 2000 && uid != 0) throw new SecurityException("Caller is not Android shell/root");
        if (!"attest".equals(method) || !AdbShellIdentity.acknowledge(uid, arg)) {
            throw new SecurityException("No matching live ADB identity challenge");
        }
        return Bundle.EMPTY;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) { throw unsupported(); }
    @Override public String getType(Uri uri) { throw unsupported(); }
    @Override public Uri insert(Uri uri, ContentValues values) { throw unsupported(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw unsupported(); }
    @Override public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) { throw unsupported(); }
    private static SecurityException unsupported() { return new SecurityException("Operation not supported"); }
}
