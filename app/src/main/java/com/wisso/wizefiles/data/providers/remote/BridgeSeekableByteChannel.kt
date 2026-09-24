package com.wisso.wizefiles.provider.remote

import android.os.BadParcelableException
import android.os.Parcel
import android.os.Parcelable
import com.wisso.wizefiles.provider.common.ForceableChannel
import com.wisso.wizefiles.provider.common.force
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

class BridgeSeekableByteChannel : SeekableByteChannel, ForceableChannel, Parcelable {
    private val local: SeekableByteChannel?
    private val remote: ISeekableChannelBridge?

    @Volatile
    private var remoteClosed = false

    constructor(channel: SeekableByteChannel) {
        local = channel
        remote = null
    }

    private constructor(parcel: Parcel) {
        local = null
        remote = ISeekableChannelBridge.Stub.asInterface(parcel.readStrongBinder())
            ?: throw BadParcelableException("Missing seekable channel bridge binder")
    }

    @Throws(IOException::class)
    override fun read(destination: ByteBuffer): Int {
        if (!destination.hasRemaining()) return 0
        return route(
            localAction = { channel -> channel.read(destination) },
            remoteAction = { bridge ->
                val transported = ByteArray(destination.remaining())
                val count = bridge.invokeBridge { failure -> readChunk(transported, failure) }
                if (count !in -1..transported.size) {
                    throw IOException("Channel bridge returned an invalid read count: " + count)
                }
                if (count > 0) destination.put(transported, 0, count)
                count
            }
        )
    }

    @Throws(IOException::class)
    override fun write(source: ByteBuffer): Int {
        if (!source.hasRemaining()) return 0
        return route(
            localAction = { channel -> channel.write(source) },
            remoteAction = { bridge ->
                val start = source.position()
                val transported = ByteArray(source.remaining())
                source.duplicate().get(transported)
                val count = bridge.invokeBridge { failure -> writeChunk(transported, failure) }
                if (count !in 0..transported.size) {
                    throw IOException("Channel bridge returned an invalid write count: " + count)
                }
                source.position(start + count)
                count
            }
        )
    }

    @Throws(IOException::class)
    override fun position(): Long = route(
        localAction = SeekableByteChannel::position,
        remoteAction = { bridge -> bridge.invokeBridge { failure -> currentPosition(failure) } }
    )

    @Throws(IOException::class)
    override fun position(newPosition: Long): SeekableByteChannel {
        require(newPosition >= 0) { "Position must not be negative" }
        route(
            localAction = { channel -> channel.position(newPosition) },
            remoteAction = { bridge -> bridge.invokeBridge { failure -> seek(newPosition, failure) } }
        )
        return this
    }

    @Throws(IOException::class)
    override fun size(): Long = route(
        localAction = SeekableByteChannel::size,
        remoteAction = { bridge -> bridge.invokeBridge { failure -> length(failure) } }
    )

    @Throws(IOException::class)
    override fun truncate(size: Long): SeekableByteChannel {
        require(size >= 0) { "Size must not be negative" }
        route(
            localAction = { channel -> channel.truncate(size) },
            remoteAction = { bridge -> bridge.invokeBridge { failure -> resize(size, failure) } }
        )
        return this
    }

    @Throws(IOException::class)
    override fun force(metaData: Boolean) {
        route(
            localAction = { channel -> channel.force(metaData) },
            remoteAction = { bridge -> bridge.invokeBridge { failure -> sync(metaData, failure) } }
        )
    }

    override fun isOpen(): Boolean = local?.isOpen ?: !remoteClosed

    @Throws(IOException::class)
    override fun close() {
        local?.let {
            it.close()
            return
        }
        if (remoteClosed) return
        checkNotNull(remote).invokeBridge { failure -> closeChannel(failure) }
        remoteClosed = true
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        val binder = remote?.asBinder() ?: LocalChannelBridge(checkNotNull(local)).asBinder()
        parcel.writeStrongBinder(binder)
    }

    private inline fun <T> route(
        localAction: (SeekableByteChannel) -> T,
        remoteAction: (ISeekableChannelBridge) -> T
    ): T {
        local?.let { return localAction(it) }
        return remoteAction(checkNotNull(remote))
    }

    private class LocalChannelBridge(
        private val channel: SeekableByteChannel
    ) : ISeekableChannelBridge.Stub() {
        override fun readChunk(destination: ByteArray, failure: BridgeFailure): Int =
            serveBridge(failure) { channel.read(ByteBuffer.wrap(destination)) } ?: -1

        override fun writeChunk(source: ByteArray, failure: BridgeFailure): Int =
            serveBridge(failure) { channel.write(ByteBuffer.wrap(source)) } ?: 0

        override fun currentPosition(failure: BridgeFailure): Long =
            serveBridge(failure) { channel.position() } ?: 0

        override fun seek(newPosition: Long, failure: BridgeFailure) {
            serveBridge(failure) { channel.position(newPosition) }
        }

        override fun length(failure: BridgeFailure): Long =
            serveBridge(failure) { channel.size() } ?: 0

        override fun resize(newLength: Long, failure: BridgeFailure) {
            serveBridge(failure) { channel.truncate(newLength) }
        }

        override fun sync(includeMetadata: Boolean, failure: BridgeFailure) {
            serveBridge(failure) { channel.force(includeMetadata) }
        }

        override fun closeChannel(failure: BridgeFailure) {
            serveBridge(failure) { channel.close() }
        }
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<BridgeSeekableByteChannel> =
            object : Parcelable.Creator<BridgeSeekableByteChannel> {
                override fun createFromParcel(parcel: Parcel) = BridgeSeekableByteChannel(parcel)
                override fun newArray(size: Int): Array<BridgeSeekableByteChannel?> =
                    arrayOfNulls(size)
            }
    }
}

fun SeekableByteChannel.toBridge(): BridgeSeekableByteChannel =
    BridgeSeekableByteChannel(this)
