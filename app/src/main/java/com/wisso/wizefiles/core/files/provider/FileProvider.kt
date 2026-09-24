// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.files.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.mime.guessFromPath
import com.wisso.wizefiles.core.files.provider.legacy.toLegacyFileProviderPath
import com.wisso.wizefiles.provider.common.size
import com.wisso.wizefiles.vault.VaultOpenSessionFileBridge
import com.wisso.wizefiles.vault.VaultOpenSessionUri
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files

class FileProvider : ContentProvider() {
    private lateinit var callbackThread: HandlerThread
    private lateinit var callbackHandler: Handler

    override fun onCreate(): Boolean {
        callbackThread = HandlerThread("FileProvider.CallbackThread")
        callbackThread.start()
        callbackHandler = Handler(callbackThread.looper)
        return true
    }

    override fun shutdown() {
        callbackThread.quitSafely()
    }

    override fun attachInfo(context: Context, info: ProviderInfo) {
        super.attachInfo(context, info)

        if (info.exported) {
            throw SecurityException("Provider must not be exported")
        }
        if (!info.grantUriPermissions) {
            throw SecurityException("Provider must grant uri permissions")
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<String?>?,
        selection: String?,
        selectionArgs: Array<String?>?,
        sortOrder: String?
    ): Cursor? {
        if (VaultOpenSessionUri.isVaultOpenSessionUri(uri)) {
            return VaultOpenSessionFileBridge.query(uri, projection)
        }
        // ContentProvider has already checked granted permissions
        val projectionColumns = projection ?: getDefaultProjection()
        val path = uri.toLegacyFileProviderPath()
        val columns = mutableListOf<String>()
        val values = mutableListOf<Any?>()
        loop@ for (column in projectionColumns) {
            @Suppress("DEPRECATION")
            when (column) {
                OpenableColumns.DISPLAY_NAME -> {
                    columns += column
                    values += path.fileName.toString()
                }
                OpenableColumns.SIZE -> {
                    val size = try {
                        path.size()
                    } catch (e: IOException) {
                        com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
                        null
                    }
                    columns += column
                    values += size
                }
                MediaStore.MediaColumns.DATA -> {
                    val file = try {
                        path.toFile()
                    } catch (e: UnsupportedOperationException) {
                        continue@loop
                    }
                    columns += column
                    values += file.absolutePath
                }
                DocumentsContract.Document.COLUMN_MIME_TYPE -> {
                    columns += column
                    values += MimeType.guessFromPath(path.toString()).value
                }
                DocumentsContract.Document.COLUMN_LAST_MODIFIED -> {
                    val lastModified = try {
                        Files.getLastModifiedTime(path).toMillis()
                    } catch (e: IOException) {
                        com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
                        null
                    }
                    columns += column
                    values += lastModified
                }
            }
        }
        return MatrixCursor(columns.toTypedArray(), 1).apply {
            addRow(values)
        }
    }

    private fun getDefaultProjection(): Array<String> =
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                Binder.getCallingUid() == Process.SYSTEM_UID
        ) {
            // com.android.internal.app.ChooserActivity.queryResolver() in Q queries with a null
            // projection (meaning all columns) on main thread but only actually needs the display
            // name (and document flags). However if we do return all the columns, we may perform
            // network requests and crash it due to StrictMode. So just work around by only
            // returning the display name in this case.
            CHOOSER_ACTIVITY_DEFAULT_PROJECTION
        } else {
            DEFAULT_PROJECTION
        }

    override fun getType(uri: Uri): String? {
        if (VaultOpenSessionUri.isVaultOpenSessionUri(uri)) {
            return VaultOpenSessionFileBridge.getType(uri)
        }
        val path = uri.toLegacyFileProviderPath()
        return MimeType.guessFromPath(path.toString()).value
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("No external inserts")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        throw UnsupportedOperationException("No external updates")
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        throw UnsupportedOperationException("No external deletes")
    }

    @Throws(FileNotFoundException::class)
    override fun openFile(uri: Uri, mode: String) =
        if (VaultOpenSessionUri.isVaultOpenSessionUri(uri)) {
            val providerContext = context ?: throw FileNotFoundException("Missing provider context")
            VaultOpenSessionFileBridge.openFile(providerContext, uri, mode, callbackHandler)
        } else {
            // ContentProvider has already checked granted permissions.
            PathParcelFileDescriptorOpener.open(uri.toLegacyFileProviderPath(), mode, callbackHandler)
        }

    companion object {
        private val DEFAULT_PROJECTION = arrayOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
            MediaStore.MediaColumns.DATA,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        private val CHOOSER_ACTIVITY_DEFAULT_PROJECTION = arrayOf(
            OpenableColumns.DISPLAY_NAME
        )
    }
}
