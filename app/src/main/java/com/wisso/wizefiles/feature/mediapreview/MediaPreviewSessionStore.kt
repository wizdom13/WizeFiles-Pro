// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.mediapreview

import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.feature.internalviewer.InternalOpenPolicy
import com.wisso.wizefiles.storage.path.AppPath
import java.util.LinkedHashMap
import java.util.UUID

data class MediaPreviewItem(
    val path: AppPath,
    val mimeType: MimeType,
    val fileItem: FileItem? = null,
    val displayName: String? = null
) {
    val userFacingName: String
        get() = displayName?.takeIf(String::isNotBlank) ?: path.name
}

data class MediaPreviewSession(
    val items: List<MediaPreviewItem>,
    val initialIndex: Int
)

/**
 * Keeps sibling lists out of Activity extras so large directories never risk TransactionTooLarge.
 * The current item is still placed in the Intent as a process-death fallback.
 */
object MediaPreviewSessionStore {
    private val sessions = object : LinkedHashMap<String, MediaPreviewSession>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, MediaPreviewSession>?
        ): Boolean = size > MAX_SESSIONS
    }

    @Synchronized
    fun create(items: List<MediaPreviewItem>, current: MediaPreviewItem): String {
        val resolvedItems = resolveSiblings(items, current)
        val currentIndex = resolvedItems.indexOfFirst { it.path == current.path }
            .coerceAtLeast(0)
        val id = UUID.randomUUID().toString()
        sessions[id] = MediaPreviewSession(resolvedItems, currentIndex)
        return id
    }

    @Synchronized
    fun get(id: String?): MediaPreviewSession? = id?.let(sessions::get)

    @Synchronized
    fun remove(id: String?) {
        if (id != null) sessions.remove(id)
    }

    internal fun resolveSiblings(
        candidates: List<MediaPreviewItem>,
        current: MediaPreviewItem
    ): List<MediaPreviewItem> {
        if (!current.isPreviewableMedia()) return listOf(current)
        val siblings = candidates.filter { it.isPreviewableMedia() }
        val resolved = if (siblings.any { it.path == current.path }) siblings else listOf(current)
        if (resolved.size <= MAX_ITEMS_PER_SESSION) return resolved
        val currentIndex = resolved.indexOfFirst { it.path == current.path }.coerceAtLeast(0)
        val start = (currentIndex - MAX_ITEMS_PER_SESSION / 2)
            .coerceIn(0, resolved.size - MAX_ITEMS_PER_SESSION)
        return resolved.subList(start, start + MAX_ITEMS_PER_SESSION).toList()
    }

    private fun MediaPreviewItem.isPreviewableMedia(): Boolean =
        when (
            InternalOpenPolicy.targetFor(
                mimeType,
                isArchiveEntry = false,
                fileName = userFacingName
            )
        ) {
            InternalOpenPolicy.Target.IMAGE_PREVIEW,
            InternalOpenPolicy.Target.VIDEO_PREVIEW -> true
            InternalOpenPolicy.Target.AUDIO_PLAYER,
            InternalOpenPolicy.Target.PDF_VIEWER,
            InternalOpenPolicy.Target.EBOOK_VIEWER,
            InternalOpenPolicy.Target.WEB_DOCUMENT_VIEWER,
            InternalOpenPolicy.Target.FONT_VIEWER,
            InternalOpenPolicy.Target.CONTAINER_BROWSER,
            InternalOpenPolicy.Target.EXTERNAL_APP -> false
        }

    private const val MAX_SESSIONS = 12
    private const val MAX_ITEMS_PER_SESSION = 2_000
}
