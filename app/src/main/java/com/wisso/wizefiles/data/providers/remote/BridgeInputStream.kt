package com.wisso.wizefiles.provider.remote

import android.os.BadParcelableException
import android.os.Parcel
import android.os.Parcelable
import java.io.IOException
import java.io.InputStream

class BridgeInputStream : InputStream, Parcelable {
    private val local: InputStream?
    private val remote: IInputBridge?

    constructor(input: InputStream) {
        local = input
        remote = null
    }

    private constructor(parcel: Parcel) {
        local = null
        remote = IInputBridge.Stub.asInterface(parcel.readStrongBinder())
            ?: throw BadParcelableException("Missing input bridge binder")
    }

    @Throws(IOException::class)
    override fun read(): Int = route(
        localAction = InputStream::read,
        remoteAction = { bridge -> bridge.invokeBridge { failure -> readByte(failure) } }
    )

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        checkRange(buffer.size, offset, length)
        if (length == 0) return 0
        return route(
            localAction = { input -> input.read(buffer, offset, length) },
            remoteAction = { bridge ->
                val transported = ByteArray(length)
                val count = bridge.invokeBridge { failure -> readChunk(transported, failure) }
                if (count !in -1..length) {
                    throw IOException("Input bridge returned an invalid byte count: " + count)
                }
                if (count > 0) transported.copyInto(buffer, offset, 0, count)
                count
            }
        )
    }

    @Throws(IOException::class)
    override fun skip(byteCount: Long): Long = route(
        localAction = { input -> input.skip(byteCount) },
        remoteAction = { bridge -> bridge.invokeBridge { failure -> skipBytes(byteCount, failure) } }
    )

    @Throws(IOException::class)
    override fun available(): Int = route(
        localAction = InputStream::available,
        remoteAction = { bridge -> bridge.invokeBridge { failure -> availableBytes(failure) } }
    )

    @Throws(IOException::class)
    override fun close() {
        route(
            localAction = InputStream::close,
            remoteAction = { bridge -> bridge.invokeBridge { failure -> closeStream(failure) } }
        )
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        val binder = remote?.asBinder() ?: LocalInputBridge(checkNotNull(local)).asBinder()
        parcel.writeStrongBinder(binder)
    }

    private inline fun <T> route(
        localAction: (InputStream) -> T,
        remoteAction: (IInputBridge) -> T
    ): T {
        local?.let { return localAction(it) }
        return remoteAction(checkNotNull(remote))
    }

    private fun checkRange(size: Int, offset: Int, length: Int) {
        if (offset < 0 || length < 0 || offset > size - length) {
            throw IndexOutOfBoundsException(
                "offset=" + offset + ", length=" + length + ", size=" + size
            )
        }
    }

    private class LocalInputBridge(private val input: InputStream) : IInputBridge.Stub() {
        override fun readByte(failure: BridgeFailure): Int =
            serveBridge(failure) { input.read() } ?: -1

        override fun readChunk(destination: ByteArray, failure: BridgeFailure): Int =
            serveBridge(failure) { input.read(destination) } ?: -1

        override fun skipBytes(byteCount: Long, failure: BridgeFailure): Long =
            serveBridge(failure) { input.skip(byteCount) } ?: 0

        override fun availableBytes(failure: BridgeFailure): Int =
            serveBridge(failure) { input.available() } ?: 0

        override fun closeStream(failure: BridgeFailure) {
            serveBridge(failure) { input.close() }
        }
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<BridgeInputStream> =
            object : Parcelable.Creator<BridgeInputStream> {
                override fun createFromParcel(parcel: Parcel) = BridgeInputStream(parcel)
                override fun newArray(size: Int): Array<BridgeInputStream?> = arrayOfNulls(size)
            }
    }
}

fun InputStream.toBridge(): BridgeInputStream = BridgeInputStream(this)
