// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

import java.nio.file.attribute.FileTime

/** Provider-neutral metadata shared by storage implementations and features. */
data class FileMetadata(
    val isDirectory: Boolean,
    val sizeBytes: Long?,
    val lastModifiedEpochMillis: Long?,
    val isSymbolicLink: Boolean = false
) {
    val isRegularFile: Boolean
        get() = !isDirectory

    fun size(): Long = sizeBytes ?: 0L

    fun lastModifiedTime(): FileTime = FileTime.fromMillis(lastModifiedEpochMillis ?: 0L)
}
