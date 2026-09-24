// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel

open class ForwardingFileChannel(private val delegate: FileChannel) : FileChannel() {
    @Throws(IOException::class)
    override fun read(destination: ByteBuffer): Int = delegate.read(destination)

    @Throws(IOException::class)
    override fun read(destinations: Array<ByteBuffer>, offset: Int, length: Int): Long =
        delegate.read(destinations, offset, length)

    @Throws(IOException::class)
    override fun read(destination: ByteBuffer, position: Long): Int =
        delegate.read(destination, position)

    @Throws(IOException::class)
    override fun write(source: ByteBuffer): Int = delegate.write(source)

    @Throws(IOException::class)
    override fun write(sources: Array<ByteBuffer>, offset: Int, length: Int): Long =
        delegate.write(sources, offset, length)

    @Throws(IOException::class)
    override fun write(source: ByteBuffer, position: Long): Int = delegate.write(source, position)

    @Throws(IOException::class)
    override fun position(): Long = delegate.position()

    @Throws(IOException::class)
    override fun position(newPosition: Long): FileChannel = apply {
        delegate.position(newPosition)
    }

    @Throws(IOException::class)
    override fun size(): Long = delegate.size()

    @Throws(IOException::class)
    override fun truncate(size: Long): FileChannel = apply {
        delegate.truncate(size)
    }

    @Throws(IOException::class)
    override fun force(metaData: Boolean) = delegate.force(metaData)

    @Throws(IOException::class)
    override fun transferTo(position: Long, count: Long, target: WritableByteChannel): Long =
        delegate.transferTo(position, count, target)

    @Throws(IOException::class)
    override fun transferFrom(source: ReadableByteChannel, position: Long, count: Long): Long =
        delegate.transferFrom(source, position, count)

    @Throws(IOException::class)
    override fun map(mode: MapMode, position: Long, size: Long): MappedByteBuffer =
        delegate.map(mode, position, size)

    @Throws(IOException::class)
    override fun lock(position: Long, size: Long, shared: Boolean): FileLock =
        delegate.lock(position, size, shared)

    @Throws(IOException::class)
    override fun tryLock(position: Long, size: Long, shared: Boolean): FileLock? =
        delegate.tryLock(position, size, shared)

    @Throws(IOException::class)
    override fun implCloseChannel() = delegate.close()
}
