// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.files.provider.legacy

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.wisso.wizefiles.BuildConfig
import com.wisso.wizefiles.provider.FileSystemProviders
import com.wisso.wizefiles.util.hasBits
import java.net.URI
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal fun Path.canOpenDirectly(mode: Int): Boolean {
    val file = try {
        toFile()
    } catch (_: UnsupportedOperationException) {
        return false
    }
    val readOnly = mode.hasBits(ParcelFileDescriptor.MODE_READ_ONLY)
    val writeOnly = mode.hasBits(ParcelFileDescriptor.MODE_WRITE_ONLY)
    val readWrite = mode.hasBits(ParcelFileDescriptor.MODE_READ_WRITE)
    val needRead = readOnly || readWrite
    val needWrite = writeOnly || readWrite
    return !((needRead && !file.canRead()) || (needWrite && !file.canWrite()))
}

internal fun Path.coerceLegacyOpenMode(mode: Int): Int {
    val file = try {
        toFile()
    } catch (_: UnsupportedOperationException) {
        return mode
    }
    return coerceLegacyOpenMode(mode, file.canRead(), file.canWrite())
}

internal fun coerceLegacyOpenMode(mode: Int, canRead: Boolean, canWrite: Boolean): Int {
    val hasReadWrite = mode.hasBits(ParcelFileDescriptor.MODE_READ_WRITE)
    val hasWriteSideEffects = mode.hasBits(
        ParcelFileDescriptor.MODE_WRITE_ONLY or
            ParcelFileDescriptor.MODE_CREATE or
            ParcelFileDescriptor.MODE_TRUNCATE or
            ParcelFileDescriptor.MODE_APPEND
    )
    if (hasReadWrite && !hasWriteSideEffects && canRead && !canWrite) {
        return mode and ParcelFileDescriptor.MODE_READ_WRITE.inv() or
            ParcelFileDescriptor.MODE_READ_ONLY
    }
    return mode
}

internal fun String.toLegacyOpenMode(): Int =
    when (this) {
        "r" -> ParcelFileDescriptor.MODE_READ_ONLY
        "w" ->
            ParcelFileDescriptor.MODE_WRITE_ONLY or
                ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_TRUNCATE
        "rw" -> ParcelFileDescriptor.MODE_READ_WRITE
        else -> ParcelFileDescriptor.parseMode(this)
    }

internal fun Int.toLegacyOpenOptions(): Set<OpenOption> =
    mutableSetOf<OpenOption>().apply {
        require(!hasBits(ParcelFileDescriptor.MODE_APPEND)) { "mode ${this@toLegacyOpenOptions}" }
        if (hasBits(ParcelFileDescriptor.MODE_READ_ONLY)
            || hasBits(ParcelFileDescriptor.MODE_READ_WRITE)) {
            this += StandardOpenOption.READ
        }
        if (hasBits(ParcelFileDescriptor.MODE_WRITE_ONLY)
            || hasBits(ParcelFileDescriptor.MODE_READ_WRITE)) {
            // Keep explicit read+write requests as rw: ProxyFileDescriptor callbacks may service
            // both read and write operations for MODE_READ_WRITE callers.
            this += StandardOpenOption.WRITE
        }
        if (hasBits(ParcelFileDescriptor.MODE_CREATE)) {
            this += StandardOpenOption.CREATE
        }
        if (hasBits(ParcelFileDescriptor.MODE_TRUNCATE)) {
            this += StandardOpenOption.TRUNCATE_EXISTING
        }
    }

val Path.fileProviderUri: Uri
    get() {
        val uriPath = Uri.encode(toUri().toString())
        return Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(BuildConfig.FILE_PROVIDER_AUTHORITY)
            .encodedPath("/$uriPath")
            .build()
    }

internal fun Uri.toLegacyFileProviderPath(): Path {
    val encodedLegacyPath = encodedPath?.removePrefix("/")
        ?: path?.removePrefix("/")
        ?: throw IllegalArgumentException("Missing file provider path")
    val decodedOnce = Uri.decode(encodedLegacyPath)
    val decodedPath = if ("://" in decodedOnce) {
        decodedOnce
    } else {
        Uri.decode(decodedOnce)
    }
    val pathUri = URI.create(decodedPath)
    val scheme = pathUri.scheme
        ?: throw IllegalArgumentException("Missing file provider path scheme")
    return FileSystemProviders[scheme].getPath(pathUri)
}
