// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage.legacy

import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.LocalAppPath
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.provider.os.LinuxFileSystemProvider
import com.wisso.wizefiles.provider.root.RootablePath
import com.wisso.wizefiles.provider.common.copyTo
import com.wisso.wizefiles.provider.common.moveTo
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.CopyOption
import java.nio.file.Files

@Throws(IOException::class)
internal fun AppPath.listLegacyDirectoryEntries(): List<AppPath> {
    val local = this as? LocalAppPath
    if (local != null && !local.requiresRootForDirectoryList()) {
        return local.file.listFiles()?.map(::LocalAppPath).orEmpty()
    }
    val directory = toLegacyPathOrNull() ?: throw IOException("Unsupported fallback path: $this")
    return try {
        Files.newDirectoryStream(directory).use { children ->
            children.map { it.toAppPath() }.toList()
        }
    } catch (e: AccessDeniedException) {
        throw IOException(ROOT_ACCESS_REQUIRED_MESSAGE, e)
    }
}

private fun LocalAppPath.requiresRootForDirectoryList(): Boolean =
    (LinuxFileSystemProvider.fileSystem.getPath(file.path) as RootablePath)
        .isRootRequired(isAttributeAccess = false)

internal const val ROOT_ACCESS_REQUIRED_MESSAGE = "Root access required"

@Throws(IOException::class)
internal fun AppPath.createLegacyDirectory() {
    val directory = requireLocalFallbackFile()
    if (!directory.mkdir()) {
        throw IOException("Failed to create directory: ${directory.path}")
    }
}

@Throws(IOException::class)
internal fun AppPath.deleteLegacyPath() {
    val file = requireLocalFallbackFile()
    if (!file.delete()) {
        throw IOException("Failed to delete path: ${file.path}")
    }
}

@Throws(IOException::class)
internal fun AppPath.copyLegacyPathTo(target: AppPath, vararg options: CopyOption) {
    val sourcePath = toLegacyPathOrNull() ?: throw IOException("Unsupported fallback path: $this")
    val targetPath = target.toLegacyPathOrNull()
        ?: throw IOException("Unsupported fallback path: $target")
    sourcePath.copyTo(targetPath, *options)
}

@Throws(IOException::class)
internal fun AppPath.moveLegacyPathTo(target: AppPath, vararg options: CopyOption) {
    val sourcePath = toLegacyPathOrNull() ?: throw IOException("Unsupported fallback path: $this")
    val targetPath = target.toLegacyPathOrNull()
        ?: throw IOException("Unsupported fallback path: $target")
    sourcePath.moveTo(targetPath, *options)
}

internal fun AppPath.requireLocalFallbackFile() =
    (this as? LocalAppPath)?.file ?: throw IOException("Unsupported fallback path: $this")
