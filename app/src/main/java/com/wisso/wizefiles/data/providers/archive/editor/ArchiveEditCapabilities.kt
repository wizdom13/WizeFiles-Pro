// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.archive.editor

import com.wisso.libarchive.Archive
import com.wisso.wizefiles.provider.archive.archiveFile
import com.wisso.wizefiles.provider.archive.isArchivePath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import java.nio.file.Path

enum class EditableArchiveFormat(val format: Int, val filter: Int) {
    ZIP(Archive.FORMAT_ZIP, Archive.FILTER_NONE),
    SEVEN_Z(Archive.FORMAT_7ZIP, Archive.FILTER_NONE),
    TAR_XZ(Archive.FORMAT_TAR, Archive.FILTER_XZ)
}

data class ArchiveEditCapability(
    val editable: Boolean,
    val format: EditableArchiveFormat? = null,
    val reason: String = ""
)

object ArchiveEditCapabilities {
    fun forArchiveFile(archiveFile: Path): ArchiveEditCapability {
        if (archiveFile.isArchivePath) {
            return ArchiveEditCapability(false, reason = "Nested archives are read-only")
        }
        val name = archiveFile.fileName?.toString()?.lowercase().orEmpty()
        if (name.endsWith(".apk") || name.endsWith(".aab") || name.endsWith(".jar")) {
            return ArchiveEditCapability(false, reason = "Signed application archives are read-only")
        }
        if (name.matches(Regex(".*\\.(z|r)\\d{2}$")) || name.endsWith(".001")) {
            return ArchiveEditCapability(false, reason = "Split archives are read-only")
        }
        val format = when {
            name.endsWith(".tar.xz") || name.endsWith(".txz") -> EditableArchiveFormat.TAR_XZ
            name.endsWith(".7z") -> EditableArchiveFormat.SEVEN_Z
            name.endsWith(".zip") -> EditableArchiveFormat.ZIP
            else -> null
        }
        return if (format == null) {
            ArchiveEditCapability(false, reason = "This archive format is read-only")
        } else {
            ArchiveEditCapability(true, format)
        }
    }

    fun forLocation(path: Path): ArchiveEditCapability =
        if (path.isArchivePath) {
            val file = path.archiveFile.toLegacyPathOrNull()
                ?: return ArchiveEditCapability(false, reason = "Archive source is unavailable")
            forArchiveFile(file)
        } else {
            ArchiveEditCapability(false, reason = "Not an archive location")
        }

    fun isEditableLocation(path: Path): Boolean = forLocation(path).editable
}
