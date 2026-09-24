package com.wisso.wizefiles.storage.legacy

import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.mime.guessFromPath
import com.wisso.wizefiles.storage.FileMetadata
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.LocalAppPath
import java.io.IOException

internal data class LegacyFileAttributeSnapshot(
    val metadataNoFollowLinks: FileMetadata,
    val symbolicLinkTarget: String?,
    val symbolicLinkTargetMetadata: FileMetadata?,
    val isHidden: Boolean,
    val mimeType: MimeType
)

internal fun AppPath.readLegacyFileAttributeSnapshot(): LegacyFileAttributeSnapshot {
    val localPath = this as? LocalAppPath
        ?: throw IOException("Unsupported AppPath for legacy attribute loading: $this")
    val file = localPath.file
    val metadataNoFollowLinks = FileMetadata(
        isDirectory = file.isDirectory,
        sizeBytes = file.takeIf { it.isFile }?.length(),
        lastModifiedEpochMillis = file.lastModified(),
        isSymbolicLink = false
    )
    val mimeType = if (metadataNoFollowLinks.isDirectory) {
        MimeType.DIRECTORY
    } else {
        MimeType.guessFromPath(rawPath)
    }
    return LegacyFileAttributeSnapshot(
        metadataNoFollowLinks = metadataNoFollowLinks,
        symbolicLinkTarget = null,
        symbolicLinkTargetMetadata = null,
        isHidden = file.isHidden,
        mimeType = mimeType
    )
}
