// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.os.media

import android.media.MediaScannerConnection
import android.mtp.MtpConstants
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.nio.channels.FileChannel
import com.wisso.wizefiles.core.app.application
import com.wisso.wizefiles.core.app.contentResolver
import com.wisso.wizefiles.hiddenapi.RestrictedHiddenApi
import com.wisso.wizefiles.provider.common.ForwardingFileChannel
import com.wisso.wizefiles.provider.root.isRunningAsRoot
import com.wisso.wizefiles.util.lazyReflectedMethod
import java.io.File
import java.io.IOException

/*
 * @see com.android.internal.content.FileSystemProvider
 * @see com.android.providers.media.scan.ModernMediaScanner.java
 */
object MediaScanner {
    private const val MIN_SCAN_INTERVAL_MS = 2_000L
    private val lastScanByPath = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun scan(file: File, isDeleted: Boolean = false) {
        if (isRunningAsRoot) {
            return
        }
        val now = android.os.SystemClock.elapsedRealtime()
        val last = lastScanByPath[file.path]
        if (last != null && now - last < MIN_SCAN_INTERVAL_MS) {
            return
        }
        lastScanByPath[file.path] = now
        MediaScannerConnection.scanFile(application, arrayOf(file.path), null) { _, _ ->
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && isDeleted) {
                // ModernMediaScanner has a bug on Android 10 that may prevent it from removing
                // certain files after their deletion. This has been fixed on Android 11 by
                // https://android.googlesource.com/platform/packages/providers/MediaProvider/+/637d133d90f49dd18bda5de219184bfa9d6c2deb
                // , but we still have to work around it for Android 10 by always trying to delete
                // the MediaStore entry ourselves.
                deleteMediaStoreEntryAsync(file)
            }
        }
    }

    @get:RequiresApi(Build.VERSION_CODES.Q)
    private val deleteMediaStoreEntryHandler by lazy {
        val thread = HandlerThread("DeleteMediaStoreEntry")
        thread.start()
        Handler(thread.looper)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deleteMediaStoreEntryAsync(file: File) {
        deleteMediaStoreEntryHandler.post {
            try {
                deleteMediaStoreEntrySync(file)
            } catch (e: Exception) {
                com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
            }
        }
    }

    @RestrictedHiddenApi
    @get:RequiresApi(Build.VERSION_CODES.Q)
    private val mediaStoreGetVolumeName by lazyReflectedMethod(
        MediaStore::class.java, "getVolumeName", File::class.java
    )

    // @see com.android.providers.media.scan.ModernMediaScanner.reconcileAndClean
    // @see https://android.googlesource.com/platform/packages/providers/MediaProvider/+/android10-release/src/com/android/providers/media/scan/ModernMediaScanner.java
    // @see https://android.googlesource.com/platform/packages/providers/MediaProvider/+/android11-release/src/com/android/providers/media/scan/ModernMediaScanner.java
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deleteMediaStoreEntrySync(file: File) {
        val file = file.canonicalFile
        val volumeName = mediaStoreGetVolumeName.invoke(null, file) as String
        val uri = MediaStore.Files.getContentUri(volumeName)
            .buildUpon()
            .appendQueryParameter("includePending", "1")
            .appendQueryParameter("deletedata", "false")
            .build()
        @Suppress("DEPRECATION")
        val where = "ifnull(format, ${MtpConstants.FORMAT_UNDEFINED}) != ${
            MtpConstants.FORMAT_ABSTRACT_AV_PLAYLIST} AND ${MediaStore.Files.FileColumns.DATA} = ?"
        val selectionArgs = arrayOf(file.absolutePath)
        contentResolver.delete(uri, where, selectionArgs)
    }

    fun createScanOnCloseFileChannel(fileChannel: FileChannel, file: File): FileChannel =
        if (isRunningAsRoot) {
            fileChannel
        } else {
            object : ForwardingFileChannel(fileChannel) {
                @Throws(IOException::class)
                override fun implCloseChannel() {
                    super.implCloseChannel()

                    scan(file)
                }
            }
        }
}
