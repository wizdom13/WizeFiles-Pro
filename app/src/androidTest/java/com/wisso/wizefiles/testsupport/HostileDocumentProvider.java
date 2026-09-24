// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.testsupport;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import java.util.Arrays;

/** Cross-package fixture that deliberately violates friendly provider assumptions. */
public final class HostileDocumentProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {
        return cursor(projection);
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            Bundle queryArgs,
            CancellationSignal cancellationSignal) {
        return cursor(projection);
    }

    private static Cursor cursor(String[] projection) {
        String[] columns = projection != null
                ? projection.clone()
                : new String[] {
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE
                };
        Object[] values = new Object[columns.length];
        for (int index = 0; index < columns.length; index++) {
            String column = columns[index];
            if (DocumentsContract.Document.COLUMN_DOCUMENT_ID.equals(column)) {
                values[index] = "hostile-id";
            } else if (DocumentsContract.Document.COLUMN_DISPLAY_NAME.equals(column)) {
                values[index] = repeated('x', 100_000);
            } else if (DocumentsContract.Document.COLUMN_MIME_TYPE.equals(column)) {
                values[index] = "text/plain";
            } else if (DocumentsContract.Document.COLUMN_SIZE.equals(column)) {
                values[index] = Long.MAX_VALUE;
            }
        }

        MatrixCursor cursor = new MatrixCursor(columns);
        cursor.addRow(values);
        Bundle extras = new Bundle();
        extras.putBoolean(DocumentsContract.EXTRA_LOADING, true);
        extras.putString(
                DocumentsContract.EXTRA_ERROR,
                "provider-error:" + repeated('e', 10_000));
        extras.putString(
                DocumentsContract.EXTRA_INFO,
                "untrusted-info:" + repeated('i', 10_000));
        cursor.setExtras(extras);
        return cursor;
    }

    private static String repeated(char value, int count) {
        char[] characters = new char[count];
        Arrays.fill(characters, value);
        return new String(characters);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) {
        throw new SecurityException("grant revoked between query and open");
    }

    @Override
    public String getType(Uri uri) {
        return "application/octet-stream";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(
            Uri uri,
            ContentValues values,
            String selection,
            String[] selectionArgs) {
        return 0;
    }
}
