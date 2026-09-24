// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import android.os.Parcelable
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime

abstract class AbstractBasicFileAttributes : BasicFileAttributes, Parcelable {
    protected abstract val lastModifiedTime: FileTime
    protected abstract val lastAccessTime: FileTime
    protected abstract val creationTime: FileTime
    protected abstract val type: BasicFileType
    protected abstract val size: Long
    protected abstract val fileKey: Parcelable

    final override fun creationTime(): FileTime = creationTime
    final override fun lastAccessTime(): FileTime = lastAccessTime
    final override fun lastModifiedTime(): FileTime = lastModifiedTime
    final override fun size(): Long = size
    final override fun fileKey(): Parcelable = fileKey

    final override fun isDirectory(): Boolean = type.matches(BasicFileType.DIRECTORY)
    final override fun isRegularFile(): Boolean = type.matches(BasicFileType.REGULAR_FILE)
    final override fun isSymbolicLink(): Boolean = type.matches(BasicFileType.SYMBOLIC_LINK)
    final override fun isOther(): Boolean = type.matches(BasicFileType.OTHER)

    private fun BasicFileType.matches(expected: BasicFileType): Boolean = this === expected
}

enum class BasicFileType {
    REGULAR_FILE,
    DIRECTORY,
    SYMBOLIC_LINK,
    OTHER
}
