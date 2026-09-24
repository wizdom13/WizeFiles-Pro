package com.wisso.wizefiles.feature.ebook

import android.content.Context
import android.content.Intent
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.feature.advancedformats.FileFormat
import com.wisso.wizefiles.feature.mediapreview.MediaPreviewItem
import com.wisso.wizefiles.provider.archive.isArchivePath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.util.extraPath

object EbookViewerIntents {
    fun create(context: Context, current: FileItem): Intent? =
        create(context, MediaPreviewItem(current.path, current.mimeType, current))

    fun create(context: Context, current: MediaPreviewItem): Intent? {
        val legacyPath = try {
            current.path.toLegacyPathOrNull()
        } catch (_: RuntimeException) {
            null
        } ?: return null
        if (legacyPath.isArchivePath) return null
        val format = FileFormat.fromFileName(current.path.name)
            ?: FileFormat.fromMimeType(current.mimeType)
        if (format?.family != FileFormat.Family.EBOOK) return null
        return Intent(context, EbookViewerActivity::class.java)
            .setType(current.mimeType.value)
            .apply { extraPath = current.path }
    }
}
