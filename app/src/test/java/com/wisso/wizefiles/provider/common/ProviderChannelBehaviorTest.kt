// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderChannelBehaviorTest {
    @Test
    fun byteBufferStreamHonorsUnsignedReadsRangesAndClose() {
        val stream = ByteBufferInputStream(ByteBuffer.wrap(byteArrayOf(0xFF.toByte(), 2, 3)))

        assertEquals(255, stream.read())
        stream.mark(8)
        val tail = ByteArray(4)
        assertEquals(2, stream.read(tail, 1, 3))
        assertArrayEquals(byteArrayOf(0, 2, 3, 0), tail)
        stream.reset()
        assertEquals(2, stream.skip(20))
        assertEquals(-1, stream.read())

        stream.close()
        assertThrows(IOException::class.java) { stream.available() }
    }

    @Test
    fun emptyBuffersAreIndependent() {
        val first = ByteBuffer::class.EMPTY
        val second = ByteBuffer::class.EMPTY

        assertEquals(0, first.remaining())
        assertEquals(0, second.remaining())
        assertNotSame(first, second)
    }

    @Test
    fun streamTransferCopiesBytesAndReportsUnreportedDelta() {
        val output = ByteArrayOutputStream()
        val progress = mutableListOf<Long>()

        ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)).copyTo(
            output,
            intervalMillis = Long.MAX_VALUE / 1_000_000L,
            listener = progress::add
        )

        assertArrayEquals(byteArrayOf(1, 2, 3, 4), output.toByteArray())
        assertEquals(listOf(4L), progress)
    }

    @Test
    fun readFullyStopsAtEndOfInput() {
        val destination = ByteArray(5)

        val count = ByteArrayInputStream(byteArrayOf(7, 8)).readFully(destination, 2, 3)

        assertEquals(2, count)
        assertArrayEquals(byteArrayOf(0, 0, 7, 8, 0), destination)
    }

    @Test
    fun forwardingSeekableChannelPreservesFluentIdentityAndClose() {
        val delegate = MemoryChannel(byteArrayOf(1, 2, 3))
        val wrapper = ForwardingPlainSeekableByteChannel(delegate)

        assertTrue(wrapper.position(1) === wrapper)
        assertTrue(wrapper.truncate(2) === wrapper)
        assertEquals(2, wrapper.size())

        wrapper.close()
        assertFalse(delegate.isOpen)
    }

    private class MemoryChannel(initial: ByteArray) : SeekableByteChannel {
        private var bytes = initial.copyOf()
        private var cursor = 0
        private var open = true

        override fun read(dst: ByteBuffer): Int {
            if (cursor >= bytes.size) return -1
            val count = minOf(dst.remaining(), bytes.size - cursor)
            dst.put(bytes, cursor, count)
            cursor += count
            return count
        }

        override fun write(src: ByteBuffer): Int {
            val count = src.remaining()
            val required = cursor + count
            if (required > bytes.size) bytes = bytes.copyOf(required)
            src.get(bytes, cursor, count)
            cursor += count
            return count
        }

        override fun position(): Long = cursor.toLong()

        override fun position(newPosition: Long): SeekableByteChannel = apply {
            require(newPosition in 0..Int.MAX_VALUE.toLong())
            cursor = newPosition.toInt()
        }

        override fun size(): Long = bytes.size.toLong()

        override fun truncate(size: Long): SeekableByteChannel = apply {
            require(size in 0..Int.MAX_VALUE.toLong())
            if (size < bytes.size) bytes = bytes.copyOf(size.toInt())
            cursor = minOf(cursor, bytes.size)
        }

        override fun isOpen(): Boolean = open

        override fun close() {
            open = false
        }
    }
}
