// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import android.os.Parcelable
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import com.wisso.wizefiles.core.files.mime.MimeType

interface ContentProviderFileAttributes : BasicFileAttributes {
    override fun lastAccessTime(): FileTime = lastModifiedTime()

    override fun creationTime(): FileTime = lastModifiedTime()

    fun mimeType(): String?

    override fun isRegularFile(): Boolean = !isDirectory

    override fun isDirectory(): Boolean = mimeType() == MimeType.DIRECTORY.value

    override fun isSymbolicLink(): Boolean = false

    override fun isOther(): Boolean = false

    override fun fileKey(): Parcelable
}
