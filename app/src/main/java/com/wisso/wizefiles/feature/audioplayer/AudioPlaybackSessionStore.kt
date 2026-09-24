// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.audioplayer

import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.feature.internalviewer.InternalOpenPolicy
import com.wisso.wizefiles.storage.path.AppPath
import java.util.LinkedHashMap
import java.util.UUID

data class AudioPlaybackItem(
    val path: AppPath,
    val mimeType: MimeType,
    val fileItem: FileItem? = null,
    val displayName: String? = null
) {
    val userFacingName: String
        get() = displayName?.takeIf(String::isNotBlank) ?: path.name
}

data class AudioPlaybackSession(
    val items: List<AudioPlaybackItem>,
    val initialIndex: Int
)

/** Keeps large audio queues out of Activity and MediaController Binder payloads. */
object AudioPlaybackSessionStore {
    private val sessions = object : LinkedHashMap<String, AudioPlaybackSession>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, AudioPlaybackSession>?
        ): Boolean = size > MAX_SESSIONS
    }

    @Synchronized
    fun create(items: List<AudioPlaybackItem>, current: AudioPlaybackItem): String {
        val resolvedItems = resolveSiblings(items, current)
        val currentIndex = resolvedItems.indexOfFirst { it.path == current.path }
            .coerceAtLeast(0)
        val id = UUID.randomUUID().toString()
        sessions[id] = AudioPlaybackSession(resolvedItems, currentIndex)
        return id
    }

    @Synchronized
    fun get(id: String?): AudioPlaybackSession? = id?.let(sessions::get)

    @Synchronized
    fun remove(id: String?) {
        if (id != null) sessions.remove(id)
    }

    internal fun resolveSiblings(
        candidates: List<AudioPlaybackItem>,
        current: AudioPlaybackItem
    ): List<AudioPlaybackItem> {
        if (!current.isAudio()) return listOf(current)
        val siblings = candidates.filter { it.isAudio() }
        val resolved = if (siblings.any { it.path == current.path }) siblings else listOf(current)
        if (resolved.size <= MAX_ITEMS_PER_SESSION) return resolved
        val currentIndex = resolved.indexOfFirst { it.path == current.path }.coerceAtLeast(0)
        val start = (currentIndex - MAX_ITEMS_PER_SESSION / 2)
            .coerceIn(0, resolved.size - MAX_ITEMS_PER_SESSION)
        return resolved.subList(start, start + MAX_ITEMS_PER_SESSION).toList()
    }

    private fun AudioPlaybackItem.isAudio(): Boolean =
        InternalOpenPolicy.targetAfterExtraction(mimeType, userFacingName) ==
            InternalOpenPolicy.Target.AUDIO_PLAYER

    private const val MAX_SESSIONS = 12
    private const val MAX_ITEMS_PER_SESSION = 2_000
}

