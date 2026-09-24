// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.remote

import android.os.BadParcelableException
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import com.wisso.wizefiles.provider.common.PosixFileMode
import com.wisso.wizefiles.provider.common.PosixFileModeBit
import com.wisso.wizefiles.provider.common.toAttribute
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.file.CopyOption
import java.nio.file.LinkOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BridgeParcelBehaviorRobolectricTest {
    @Test
    fun serializablePayloadRoundTripsWithItsType() {
        val restored = roundTrip(BridgeSerializable("value"), BridgeSerializable.CREATOR)

        assertEquals("value", restored.value<String>())
    }

    @Test
    fun objectPayloadRequiresParcelableAndRoundTrips() {
        assertThrows(IllegalArgumentException::class.java) { BridgeObject(Any()) }

        val restored = roundTrip(
            BridgeObject(Bundle().apply { putString("key", "value") }),
            BridgeObject.CREATOR
        )

        assertEquals("value", restored.value<Bundle>().getString("key"))
    }

    @Test
    fun bridgeFailureAcceptsSupportedExceptionsOnce() {
        val failure = BridgeFailure().apply { value = IOException("offline") }
        val restored = roundTrip(failure, BridgeFailure.CREATOR)

        assertTrue(restored.value is IOException)
        assertEquals("offline", restored.value?.message)
        assertThrows(IllegalStateException::class.java) {
            failure.value = IOException("second")
        }
    }

    @Test
    fun copyOptionsRoundTripAndRejectOversizedCounts() {
        val restored = roundTrip(
            BridgeCopyOptions(arrayOf<CopyOption>(LinkOption.NOFOLLOW_LINKS)),
            BridgeCopyOptions.CREATOR
        )
        assertEquals(listOf(LinkOption.NOFOLLOW_LINKS), restored.value.toList())

        val parcel = Parcel.obtain()
        try {
            parcel.writeInt(129)
            parcel.setDataPosition(0)
            assertThrows(BadParcelableException::class.java) {
                BridgeCopyOptions.CREATOR.createFromParcel(parcel)
            }
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun fileModeAttributesRoundTripWithoutLosingBits() {
        val bits = setOf(PosixFileModeBit.OWNER_READ, PosixFileModeBit.OWNER_WRITE)
        val restored = roundTrip(
            BridgeFileAttributes(arrayOf(bits.toAttribute())),
            BridgeFileAttributes.CREATOR
        )

        assertEquals(bits, PosixFileMode.fromAttribute(restored.value.single()))
    }

    @Test
    fun inputBridgeTransfersReadsAcrossItsBinder() {
        val input = roundTrip(
            BridgeInputStream(ByteArrayInputStream(byteArrayOf(1, 2, 3))),
            BridgeInputStream.CREATOR
        )
        val destination = ByteArray(4)

        assertEquals(2, input.read(destination, 1, 2))
        assertEquals(listOf<Byte>(0, 1, 2, 0), destination.toList())
        assertEquals(3, input.read())
        assertEquals(-1, input.read())
    }

    @Test
    fun seekableBridgeAdvancesOnlyByAcknowledgedBytes() {
        val channel = roundTrip(
            BridgeSeekableByteChannel(MemoryChannel(byteArrayOf(4, 5, 6))),
            BridgeSeekableByteChannel.CREATOR
        )
        val destination = ByteBuffer.allocate(2)

        assertEquals(2, channel.read(destination))
        assertEquals(2L, channel.position())
        channel.position(1)
        assertEquals(1, channel.write(ByteBuffer.wrap(byteArrayOf(9))))
        channel.position(0)
        val all = ByteBuffer.allocate(3)
        assertEquals(3, channel.read(all))
        assertEquals(listOf<Byte>(4, 9, 6), all.array().toList())
    }

    private fun <T : Parcelable> roundTrip(value: T, creator: Parcelable.Creator<T>): T {
        val parcel = Parcel.obtain()
        return try {
            value.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            creator.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }

    private class MemoryChannel(initial: ByteArray) : SeekableByteChannel {
        private var content = initial.copyOf()
        private var cursor = 0
        private var open = true

        override fun read(destination: ByteBuffer): Int {
            if (cursor >= content.size) return -1
            val count = minOf(destination.remaining(), content.size - cursor)
            destination.put(content, cursor, count)
            cursor += count
            return count
        }

        override fun write(source: ByteBuffer): Int {
            val count = source.remaining()
            val required = cursor + count
            if (required > content.size) content = content.copyOf(required)
            source.get(content, cursor, count)
            cursor += count
            return count
        }

        override fun position(): Long = cursor.toLong()

        override fun position(newPosition: Long): SeekableByteChannel = apply {
            cursor = newPosition.toInt()
        }

        override fun size(): Long = content.size.toLong()

        override fun truncate(size: Long): SeekableByteChannel = apply {
            if (size < content.size) content = content.copyOf(size.toInt())
            cursor = minOf(cursor, content.size)
        }

        override fun isOpen(): Boolean = open

        override fun close() {
            open = false
        }
    }
}
