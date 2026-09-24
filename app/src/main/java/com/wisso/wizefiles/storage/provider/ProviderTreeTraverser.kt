// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage.provider

import com.wisso.wizefiles.storage.FileMetadata
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/**
 * Provider-neutral tree traversal for FTP, SFTP, SMB, SAF and other NIO-backed storage.
 */
object ProviderTreeTraverser {
    @Throws(IOException::class)
    fun walkPreOrder(
        source: Path,
        onDirectory: (Path, FileMetadata) -> Unit,
        onFile: (Path, FileMetadata) -> Unit
    ) {
        walk(source, onDirectory, onFile)
    }

    @Throws(IOException::class)
    private fun walk(
        path: Path,
        onDirectory: (Path, FileMetadata) -> Unit,
        onFile: (Path, FileMetadata) -> Unit
    ) {
        val attributes = Files.readAttributes(
            path,
            BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS
        )
        val metadata = FileMetadata(
            isDirectory = attributes.isDirectory,
            sizeBytes = if (attributes.isRegularFile) attributes.size() else 0L,
            lastModifiedEpochMillis = attributes.lastModifiedTime()?.toMillis(),
            isSymbolicLink = attributes.isSymbolicLink
        )
        if (!attributes.isDirectory) {
            onFile(path, metadata)
            return
        }

        onDirectory(path, metadata)
        Files.newDirectoryStream(path).use { children ->
            for (child in children) {
                walk(child, onDirectory, onFile)
            }
        }
    }
}
