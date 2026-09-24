// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.util

import com.wisso.wizefiles.provider.common.ByteString
import com.wisso.wizefiles.provider.common.ByteStringBuilder

@JvmInline
value class BytePathName(val value: ByteString) {
    val fileName: ByteString?
        get() = value.pathLeaf()

    val directoryName: ByteString?
        get() = value.pathParent()

    companion object {
        const val SEPARATOR = '/'.code.toByte()
    }
}

fun ByteString.asPathName(): BytePathName {
    require(canRepresentPath())
    return BytePathName(this)
}

fun ByteString.asPathNameOrNull(): BytePathName? =
    takeIf { it.canRepresentPath() }?.let(::BytePathName)

@JvmInline
value class ByteFileName(val value: ByteString) {
    val singleExtension: ByteString
        get() = value.lastSuffix()

    val extensions: ByteString
        get() {
            val last = singleExtension
            if (last.isEmpty() || last.toString().lowercase() !in COMPOUND_ENDINGS) {
                return last
            }
            val prefixEnd = value.length - last.length - 1
            if (prefixEnd <= 0) {
                return last
            }
            val previous = value.substring(0, prefixEnd).lastSuffix()
            if (previous.isEmpty()) {
                return last
            }
            return ByteStringBuilder()
                .append(previous)
                .append(EXTENSION_SEPARATOR)
                .append(last)
                .toByteString()
        }

    val baseName: ByteString
        get() {
            val suffix = extensions
            return if (suffix.isEmpty()) {
                value
            } else {
                value.substring(0, value.length - suffix.length - 1)
            }
        }

    companion object {
        const val EXTENSION_SEPARATOR = '.'.code.toByte()

        private val COMPOUND_ENDINGS = setOf("bz", "bz2", "gz", "sit", "xz", "z")
    }
}

fun ByteString.asFileName(): ByteFileName {
    require(canRepresentFileName())
    return ByteFileName(this)
}

fun ByteString.asFileNameOrNull(): ByteFileName? =
    takeIf { it.canRepresentFileName() }?.let(::ByteFileName)

private fun ByteString.pathLeaf(): ByteString? {
    val separator = lastIndexOf(BytePathName.SEPARATOR)
    val start = separator + 1
    return if (start >= length) null else substring(start)
}

private fun ByteString.pathParent(): ByteString? {
    val separator = lastIndexOf(BytePathName.SEPARATOR)
    if (separator < 0) {
        return null
    }
    var end = separator
    while (end > 0 && this[end - 1] == BytePathName.SEPARATOR) {
        --end
    }
    return substring(0, end)
}

private fun ByteString.lastSuffix(): ByteString {
    val separator = lastIndexOf(ByteFileName.EXTENSION_SEPARATOR)
    return if (separator < 0) ByteString.EMPTY else substring(separator + 1)
}

private fun ByteString.canRepresentPath(): Boolean =
    isNotEmpty() && !contains('\u0000'.code.toByte())

private fun ByteString.canRepresentFileName(): Boolean =
    canRepresentPath() && !contains(BytePathName.SEPARATOR)
