// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.searchindex

import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.mime.guessFromPath
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.feature.filebrowser.getCollationKeyForFileName
import com.wisso.wizefiles.storage.FileMetadata
import com.wisso.wizefiles.storage.path.LocalAppPath
import java.io.File
import java.text.Collator
import java.util.Locale

data class SearchIndexRecord(
    val rootPath: String,
    val path: String,
    val parentPath: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedMillis: Long,
    val isHidden: Boolean,
    val mimeType: String,
    val generation: Long
) {
    val normalizedName: String
        get() = normalizeSearchText(name)
}

data class SearchIndexStatus(
    val state: State,
    val itemCount: Long,
    val lastUpdatedMillis: Long,
    val indexedRoots: List<String>
) {
    enum class State { EMPTY, INDEXING, READY, FAILED }
}

internal fun normalizeSearchText(value: String): String = value.lowercase(Locale.ROOT)

internal fun SearchIndexRecord.toFileItem(): FileItem {
    val metadata = FileMetadata(
        isDirectory = isDirectory,
        sizeBytes = sizeBytes.takeIf { !isDirectory && it >= 0L },
        lastModifiedEpochMillis = modifiedMillis.takeIf { it >= 0L },
        isSymbolicLink = false
    )
    return FileItem(
        path = LocalAppPath(File(path)),
        nameCollationKey = Collator.getInstance().getCollationKeyForFileName(name),
        attributesNoFollowLinks = metadata,
        symbolicLinkTarget = null,
        symbolicLinkTargetAttributes = null,
        isHidden = isHidden,
        mimeType = if (isDirectory) MimeType.DIRECTORY else {
            runCatching { MimeType(mimeType) }.getOrDefault(MimeType.guessFromPath(path))
        }
    )
}
