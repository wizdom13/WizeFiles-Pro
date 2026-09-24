// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import android.os.Parcelable
import java.nio.file.attribute.PosixFileAttributes as NioPosixFileAttributes
import java.nio.file.attribute.PosixFilePermission

interface PosixFileAttributes : NioPosixFileAttributes {
    fun type(): PosixFileType
    fun mode(): Set<PosixFileModeBit>?
    fun seLinuxContext(): ByteString?

    override fun fileKey(): Parcelable
    override fun owner(): PosixUser?
    override fun group(): PosixGroup?

    override fun permissions(): Set<PosixFilePermission>? = mode()?.toPermissions()

    override fun isDirectory(): Boolean = type() === PosixFileType.DIRECTORY
    override fun isRegularFile(): Boolean = type() === PosixFileType.REGULAR_FILE
    override fun isSymbolicLink(): Boolean = type() === PosixFileType.SYMBOLIC_LINK

    override fun isOther(): Boolean {
        val kind = type()
        return kind !== PosixFileType.DIRECTORY &&
            kind !== PosixFileType.REGULAR_FILE &&
            kind !== PosixFileType.SYMBOLIC_LINK
    }
}
