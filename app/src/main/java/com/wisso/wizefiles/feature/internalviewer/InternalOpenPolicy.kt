// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.internalviewer

import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.feature.advancedformats.FileFormat

/** Keeps WizeFiles-owned viewer routing decisions out of browser controllers. */
object InternalOpenPolicy {
    enum class Target {
        IMAGE_PREVIEW,
        VIDEO_PREVIEW,
        AUDIO_PLAYER,
        PDF_VIEWER,
        EBOOK_VIEWER,
        WEB_DOCUMENT_VIEWER,
        FONT_VIEWER,
        CONTAINER_BROWSER,
        EXTERNAL_APP
    }

    fun targetFor(
        mimeType: MimeType,
        isArchiveEntry: Boolean,
        fileName: String? = null
    ): Target =
        if (isArchiveEntry) Target.EXTERNAL_APP else targetAfterExtraction(mimeType, fileName)

    /** Returns the focused viewer for a normal file or an entry after guarded extraction. */
    fun targetAfterExtraction(mimeType: MimeType, fileName: String? = null): Target {
        val format = fileName?.let(FileFormat::fromFileName) ?: FileFormat.fromMimeType(mimeType)
        return when {
            FontOpenPolicy.supports(fileName, mimeType.value) -> Target.FONT_VIEWER
            format?.family == FileFormat.Family.EBOOK -> Target.EBOOK_VIEWER
            format?.family == FileFormat.Family.WEB_DOCUMENT -> Target.WEB_DOCUMENT_VIEWER
            format?.family == FileFormat.Family.IMAGE -> Target.IMAGE_PREVIEW
            format?.family == FileFormat.Family.VIDEO -> Target.VIDEO_PREVIEW
            format?.family == FileFormat.Family.AUDIO -> Target.AUDIO_PLAYER
            format?.family == FileFormat.Family.PDF -> Target.PDF_VIEWER
            mimeType.type == "image" -> Target.IMAGE_PREVIEW
            mimeType.type == "video" -> Target.VIDEO_PREVIEW
            mimeType.type == "audio" -> Target.AUDIO_PLAYER
            mimeType.value.equals(PDF_MIME_TYPE, ignoreCase = true) -> Target.PDF_VIEWER
            else -> Target.EXTERNAL_APP
        }
    }

    /**
     * Maps a confidently detected format to its eventual WizeFiles destination. Callers must not
     * launch this result until the corresponding backend is installed by its focused feature PR.
     */
    fun plannedTargetFor(format: FileFormat): Target = when (format.family) {
        FileFormat.Family.IMAGE -> Target.IMAGE_PREVIEW
        FileFormat.Family.VIDEO -> Target.VIDEO_PREVIEW
        FileFormat.Family.AUDIO -> Target.AUDIO_PLAYER
        FileFormat.Family.PDF -> Target.PDF_VIEWER
        FileFormat.Family.EBOOK -> Target.EBOOK_VIEWER
        FileFormat.Family.WEB_DOCUMENT -> Target.WEB_DOCUMENT_VIEWER
        FileFormat.Family.ARCHIVE,
        FileFormat.Family.DISK_IMAGE -> Target.CONTAINER_BROWSER
        FileFormat.Family.UNKNOWN -> Target.EXTERNAL_APP
    }

    private const val PDF_MIME_TYPE = "application/pdf"
}
