// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.mediapreview

import android.content.Context
import android.content.Intent
import com.wisso.wizefiles.BuildConfig
import com.wisso.wizefiles.core.files.mime.asMimeTypeOrNull
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.feature.internalviewer.InternalOpenPolicy
import com.wisso.wizefiles.provider.archive.isArchivePath
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.util.extraPath

object MediaPreviewIntents {
    internal const val EXTRA_SESSION_ID =
        "${BuildConfig.APPLICATION_ID}.extra.MEDIA_PREVIEW_SESSION_ID"

    fun create(
        context: Context,
        current: FileItem,
        siblings: List<FileItem>
    ): Intent? = create(
        context,
        MediaPreviewItem(current.path, current.mimeType, current),
        siblings.map { MediaPreviewItem(it.path, it.mimeType, it) }
    )

    fun create(
        context: Context,
        current: MediaPreviewItem,
        siblings: List<MediaPreviewItem> = listOf(current)
    ): Intent? {
        val legacyPath = try {
            current.path.toLegacyPathOrNull()
        } catch (_: RuntimeException) {
            null
        } ?: return null
        val target = InternalOpenPolicy.targetFor(current.mimeType, legacyPath.isArchivePath)
        if (
            target != InternalOpenPolicy.Target.IMAGE_PREVIEW &&
            target != InternalOpenPolicy.Target.VIDEO_PREVIEW
        ) {
            return null
        }
        val sessionId = MediaPreviewSessionStore.create(siblings, current)
        return Intent(context, MediaPreviewActivity::class.java)
            .setType(current.mimeType.value)
            .putExtra(EXTRA_SESSION_ID, sessionId)
            .apply { extraPath = current.path }
    }

    internal fun fallbackItem(
        path: AppPath?,
        mimeType: String?,
        displayName: String? = null
    ): MediaPreviewItem? {
        if (path == null || mimeType == null) return null
        val parsedMimeType = mimeType.asMimeTypeOrNull() ?: return null
        return MediaPreviewItem(path, parsedMimeType, displayName = displayName)
    }
}
