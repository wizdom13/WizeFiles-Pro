// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.imageloader.coil.legacy

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toLocalFileOrNull
import java.io.IOException
import java.io.InputStream
import java.net.URI

internal val AppPath.isDocumentUriLike: Boolean
    get() = rawPath.startsWith("${android.content.ContentResolver.SCHEME_CONTENT}://")

internal val AppPath.isFtpUriLike: Boolean
    get() = rawPath.startsWith("ftp://")

internal val AppPath.isRemoteUriLike: Boolean
    get() {
        val scheme = runCatching { URI.create(rawPath).scheme?.lowercase() }.getOrNull()
        return when (scheme) {
            null, "", "file", android.content.ContentResolver.SCHEME_CONTENT -> false
            else -> true
        }
    }

internal fun AppPath.openInputStream(context: Context): InputStream {
    toLocalFileOrNull()?.let { return it.inputStream() }
    val uri = toUriOrNull() ?: throw IOException("Unsupported AppPath: $this")
    return checkNotNull(context.contentResolver.openInputStream(uri)) {
        "Cannot open input stream for $uri"
    }
}

internal fun AppPath.openReadOnlyParcelFileDescriptor(context: Context): ParcelFileDescriptor {
    toLocalFileOrNull()?.let {
        return ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    val uri = toUriOrNull() ?: throw IOException("Unsupported AppPath: $this")
    return checkNotNull(context.contentResolver.openFileDescriptor(uri, "r")) {
        "Cannot open descriptor for $uri"
    }
}

private fun AppPath.toUriOrNull(): Uri? = runCatching { Uri.parse(rawPath) }.getOrNull()
