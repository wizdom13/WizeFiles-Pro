// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer

class ByteBufferInputStream(source: ByteBuffer) : InputStream() {
    private var source: ByteBuffer? = source

    override fun read(): Int {
        val current = openBuffer()
        return if (current.hasRemaining()) current.get().toInt() and BYTE_MASK else -1
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        checkRange(bytes.size, offset, length)
        val current = openBuffer()
        if (length == 0) return 0
        if (!current.hasRemaining()) return -1

        val count = minOf(length, current.remaining())
        current.get(bytes, offset, count)
        return count
    }

    override fun skip(byteCount: Long): Long {
        val current = openBuffer()
        if (byteCount <= 0) return 0
        val count = minOf(byteCount, current.remaining().toLong()).toInt()
        current.position(current.position() + count)
        return count.toLong()
    }

    override fun available(): Int = openBuffer().remaining()

    override fun markSupported(): Boolean = true

    override fun mark(readLimit: Int) {
        openBuffer().mark()
    }

    override fun reset() {
        openBuffer().reset()
    }

    override fun close() {
        source = null
    }

    private fun openBuffer(): ByteBuffer = source ?: throw IOException("Stream is closed")

    private fun checkRange(size: Int, offset: Int, length: Int) {
        if (offset < 0 || length < 0 || offset > size - length) {
            throw IndexOutOfBoundsException("offset=$offset, length=$length, size=$size")
        }
    }

    private companion object {
        const val BYTE_MASK = 0xFF
    }
}
