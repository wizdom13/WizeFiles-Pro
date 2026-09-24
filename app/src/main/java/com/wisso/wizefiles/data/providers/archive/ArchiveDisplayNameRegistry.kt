// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.archive

import java.nio.file.Path

/**
 * Keeps user-facing archive names separate from provider identities such as opaque content URI IDs.
 * Entries are process-local UI metadata; filesystem identity continues to come only from archiveFile.
 */
internal object ArchiveDisplayNameRegistry {
    private const val MAX_ENTRIES = 64

    private val names = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, String>?
        ): Boolean = size > MAX_ENTRIES
    }

    @Synchronized
    fun remember(path: Path, displayName: String?) {
        if (!path.isArchivePath) return
        val normalized = displayName?.trim()?.takeIf(String::isNotEmpty) ?: return
        names[path.archiveFile.rawPath] = normalized
    }

    @Synchronized
    fun get(path: Path): String? {
        if (!path.isArchivePath) return null
        return names[path.archiveFile.rawPath]
    }

    @Synchronized
    internal fun clearForTests() {
        names.clear()
    }
}
