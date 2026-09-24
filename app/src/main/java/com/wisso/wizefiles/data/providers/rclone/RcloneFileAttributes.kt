// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.rclone

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.time.Instant

internal data class RcloneFileAttributes(
    private val path: String,
    private val directory: Boolean,
    private val length: Long,
    private val modifiedAt: Instant?
) : BasicFileAttributes {
    private val modifiedTime = FileTime.from(modifiedAt ?: Instant.EPOCH)

    override fun lastModifiedTime(): FileTime = modifiedTime

    override fun lastAccessTime(): FileTime = modifiedTime

    override fun creationTime(): FileTime = modifiedTime

    override fun isRegularFile(): Boolean = !directory

    override fun isDirectory(): Boolean = directory

    override fun isSymbolicLink(): Boolean = false

    override fun isOther(): Boolean = false

    override fun size(): Long = if (directory) 0L else length

    override fun fileKey(): Any = path
}
