// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import android.system.OsConstants
import java.nio.file.attribute.BasicFileAttributes

enum class PosixFileType(val mode: Int) {
    UNKNOWN(0),
    DIRECTORY(OsConstants.S_IFDIR),
    CHARACTER_DEVICE(OsConstants.S_IFCHR),
    BLOCK_DEVICE(OsConstants.S_IFBLK),
    REGULAR_FILE(OsConstants.S_IFREG),
    FIFO(OsConstants.S_IFIFO),
    SYMBOLIC_LINK(OsConstants.S_IFLNK),
    SOCKET(OsConstants.S_IFSOCK);

    companion object {
        private val typesByMask = entries
            .filterNot { it === UNKNOWN }
            .associateBy(PosixFileType::mode)

        fun fromMode(mode: Int): PosixFileType =
            typesByMask[mode and OsConstants.S_IFMT] ?: UNKNOWN
    }
}

val BasicFileAttributes.posixFileType: PosixFileType
    get() {
        if (this is PosixFileAttributes) return type()
        return when {
            isDirectory -> PosixFileType.DIRECTORY
            isRegularFile -> PosixFileType.REGULAR_FILE
            isSymbolicLink -> PosixFileType.SYMBOLIC_LINK
            else -> PosixFileType.UNKNOWN
        }
    }
