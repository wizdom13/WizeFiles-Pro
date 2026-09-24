// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.content.resolver

import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException
import java.nio.file.NoSuchFileException
import java.io.FileNotFoundException

class ResolverException : Exception {
    constructor(message: String?) : super(message)

    constructor(cause: Throwable?) : super(cause)

    fun toFileSystemException(file: String?, other: String? = null): FileSystemException =
        when (cause) {
            is FileNotFoundException -> NoSuchFileException(file, other, message)
            is SecurityException -> AccessDeniedException(file, other, message)
            else -> FileSystemException(file, other, message)
        }.apply { initCause(this@ResolverException) }
}
