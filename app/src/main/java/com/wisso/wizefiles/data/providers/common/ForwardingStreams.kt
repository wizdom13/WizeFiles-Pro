// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

open class ForwardingInputStream(private val source: InputStream) : InputStream() {
    @Throws(IOException::class)
    override fun read(): Int = source.read()

    @Throws(IOException::class)
    override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
        source.read(bytes, offset, length)

    @Throws(IOException::class)
    override fun skip(byteCount: Long): Long = source.skip(byteCount)

    @Throws(IOException::class)
    override fun available(): Int = source.available()

    override fun mark(readLimit: Int) = source.mark(readLimit)

    @Throws(IOException::class)
    override fun reset() = source.reset()

    override fun markSupported(): Boolean = source.markSupported()

    @Throws(IOException::class)
    override fun close() = source.close()
}

open class ForwardingOutputStream(private val sink: OutputStream) : OutputStream() {
    @Throws(IOException::class)
    override fun write(value: Int) = sink.write(value)

    @Throws(IOException::class)
    override fun write(bytes: ByteArray, offset: Int, length: Int) =
        sink.write(bytes, offset, length)

    @Throws(IOException::class)
    override fun flush() = sink.flush()

    @Throws(IOException::class)
    override fun close() = sink.close()
}
