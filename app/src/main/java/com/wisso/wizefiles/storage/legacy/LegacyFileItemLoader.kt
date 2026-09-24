// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage.legacy

import androidx.annotation.WorkerThread
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.core.files.model.loadFileItem
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.mime.guessFromPath
import com.wisso.wizefiles.storage.FileMetadata
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.LocalAppPath
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

internal data class FileItemSnapshot(
    val metadataNoFollowLinks: FileMetadata,
    val symbolicLinkTarget: String?,
    val symbolicLinkTargetMetadata: FileMetadata?,
    val isHidden: Boolean,
    val mimeType: MimeType
)

@WorkerThread
@Throws(IOException::class)
fun Path.loadFileItem(): FileItem = toAppPath().loadFileItem()

internal fun AppPath.readFileItemSnapshot(): FileItemSnapshot {
    val localPath = this as? LocalAppPath
    if (localPath != null) {
        val file = localPath.file
        val metadata = FileMetadata(
            isDirectory = file.isDirectory,
            sizeBytes = file.takeIf { it.isFile }?.length(),
            lastModifiedEpochMillis = file.lastModified(),
            isSymbolicLink = false
        )
        val mimeType = if (metadata.isDirectory) {
            MimeType.DIRECTORY
        } else {
            MimeType.guessFromPath(rawPath)
        }
        return FileItemSnapshot(
            metadataNoFollowLinks = metadata,
            symbolicLinkTarget = null,
            symbolicLinkTargetMetadata = null,
            isHidden = file.isHidden,
            mimeType = mimeType
        )
    }
    val legacyPath = toLegacyPathOrNull()
        ?: throw IOException("Unsupported AppPath for legacy file item loading: $this")
    val attributes = Files.readAttributes(
        legacyPath,
        BasicFileAttributes::class.java,
        LinkOption.NOFOLLOW_LINKS
    )
    val metadata = FileMetadata(
        isDirectory = attributes.isDirectory,
        sizeBytes = attributes.takeIf { !it.isDirectory }?.size(),
        lastModifiedEpochMillis = attributes.lastModifiedTime().toMillis(),
        isSymbolicLink = attributes.isSymbolicLink
    )
    return FileItemSnapshot(
        metadataNoFollowLinks = metadata,
        symbolicLinkTarget = null,
        symbolicLinkTargetMetadata = null,
        isHidden = runCatching { Files.isHidden(legacyPath) }.getOrDefault(false),
        mimeType = if (metadata.isDirectory) MimeType.DIRECTORY else MimeType.guessFromPath(rawPath)
    )
}

internal fun detectLocalMimeType(path: AppPath, metadata: FileMetadata): String =
    if (metadata.isDirectory) {
        MimeType.DIRECTORY.value
    } else {
        MimeType.guessFromPath(path.rawPath).value
    }
