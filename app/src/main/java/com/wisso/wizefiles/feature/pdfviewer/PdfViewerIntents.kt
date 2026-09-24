// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.pdfviewer

import android.content.Context
import android.content.Intent
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.feature.internalviewer.InternalOpenPolicy
import com.wisso.wizefiles.feature.mediapreview.MediaPreviewItem
import com.wisso.wizefiles.provider.archive.isArchivePath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.util.extraPath

object PdfViewerIntents {
    fun create(context: Context, current: FileItem): Intent? =
        create(context, MediaPreviewItem(current.path, current.mimeType, current))

    fun create(context: Context, current: MediaPreviewItem): Intent? {
        val legacyPath = try {
            current.path.toLegacyPathOrNull()
        } catch (_: RuntimeException) {
            null
        } ?: return null
        if (
            InternalOpenPolicy.targetFor(current.mimeType, legacyPath.isArchivePath) !=
            InternalOpenPolicy.Target.PDF_VIEWER
        ) {
            return null
        }
        return Intent(context, PdfViewerActivity::class.java)
            .setType(current.mimeType.value)
            .apply { extraPath = current.path }
    }
}
