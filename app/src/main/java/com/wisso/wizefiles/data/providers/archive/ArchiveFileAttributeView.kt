// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.archive

import com.wisso.wizefiles.provider.common.ByteString
import com.wisso.wizefiles.provider.common.PosixFileAttributeView
import com.wisso.wizefiles.provider.common.PosixFileModeBit
import com.wisso.wizefiles.provider.common.PosixGroup
import com.wisso.wizefiles.provider.common.PosixUser
import java.io.IOException
import java.nio.file.Path
import java.nio.file.attribute.FileTime

internal class ArchiveFileAttributeView(private val path: Path) : PosixFileAttributeView {
    override fun name(): String = NAME

    @Throws(IOException::class)
    override fun readAttributes(): ArchiveFileAttributes {
        val fileSystem = path.fileSystem as ArchiveFileSystem
        val entry = fileSystem.getEntry(path)
        return ArchiveFileAttributes.from(fileSystem.archiveFile, entry)
    }

    override fun setTimes(
        lastModifiedTime: FileTime?,
        lastAccessTime: FileTime?,
        createTime: FileTime?
    ) {
        throw UnsupportedOperationException()
    }

    override fun setOwner(owner: PosixUser) {
        throw UnsupportedOperationException()
    }

    override fun setGroup(group: PosixGroup) {
        throw UnsupportedOperationException()
    }

    override fun setMode(mode: Set<PosixFileModeBit>) {
        throw UnsupportedOperationException()
    }

    override fun setSeLinuxContext(context: ByteString) {
        throw UnsupportedOperationException()
    }

    override fun restoreSeLinuxContext() {
        throw UnsupportedOperationException()
    }

    companion object {
        private const val NAME = "archive"

        val SUPPORTED_NAMES = setOf("basic", "posix", NAME)
    }
}
