// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.android.compat

import java.io.IOException
import java.io.InputStream
import kotlin.reflect.KClass

fun KClass<InputStream>.nullInputStream(): InputStream = ClosedAwareEmptyInputStream()

private class ClosedAwareEmptyInputStream : InputStream() {
    private var open = true

    override fun read(): Int {
        requireOpen()
        return END_OF_STREAM
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || offset > bytes.size - length) {
            throw IndexOutOfBoundsException(
                "offset=" + offset + ", length=" + length + ", size=" + bytes.size
            )
        }
        requireOpen()
        return if (length == 0) 0 else END_OF_STREAM
    }

    override fun skip(byteCount: Long): Long {
        requireOpen()
        return 0
    }

    override fun available(): Int {
        requireOpen()
        return 0
    }

    override fun close() {
        open = false
    }

    private fun requireOpen() {
        if (!open) throw IOException("Stream is closed")
    }

    private companion object {
        const val END_OF_STREAM = -1
    }
}
