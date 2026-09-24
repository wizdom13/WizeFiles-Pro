package com.wisso.wizefiles.provider.common

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

fun forwardingChannel(channel: SeekableByteChannel): SeekableByteChannel =
    when (channel) {
        is ForceableChannel -> ForwardingForceableSeekableByteChannel(channel)
        else -> ForwardingPlainSeekableByteChannel(channel)
    }

open class ForwardingPlainSeekableByteChannel(channel: SeekableByteChannel) :
    ForwardingSeekableByteChannel(channel) {
    init {
        require(channel !is ForceableChannel) { "Forceable channels require the forceable wrapper" }
    }
}

open class ForwardingForceableSeekableByteChannel(
    private val forceableDelegate: SeekableByteChannel
) : ForwardingSeekableByteChannel(forceableDelegate), ForceableChannel {
    init {
        require(forceableDelegate is ForceableChannel) { "The wrapped channel must support force()" }
    }

    @Throws(IOException::class)
    override fun force(metaData: Boolean) {
        (forceableDelegate as ForceableChannel).force(metaData)
    }
}

abstract class ForwardingSeekableByteChannel internal constructor(
    private val delegate: SeekableByteChannel
) : SeekableByteChannel {
    @Throws(IOException::class)
    override fun read(destination: ByteBuffer): Int = delegate.read(destination)

    @Throws(IOException::class)
    override fun write(source: ByteBuffer): Int = delegate.write(source)

    @Throws(IOException::class)
    override fun position(): Long = delegate.position()

    @Throws(IOException::class)
    override fun position(newPosition: Long): SeekableByteChannel = apply {
        delegate.position(newPosition)
    }

    @Throws(IOException::class)
    override fun size(): Long = delegate.size()

    @Throws(IOException::class)
    override fun truncate(size: Long): SeekableByteChannel = apply {
        delegate.truncate(size)
    }

    override fun isOpen(): Boolean = delegate.isOpen

    @Throws(IOException::class)
    override fun close() = delegate.close()
}
