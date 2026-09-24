package com.wisso.wizefiles.feature.advancedformats.image

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AdvancedImagePreviewDecoderTest {
    @Test
    fun `decodes uncompressed truecolor tga with origin`() {
        val data = ByteArray(18 + 6)
        data[2] = 2
        data[12] = 2
        data[14] = 1
        data[16] = 24
        data[17] = 0x20
        data[18] = 0
        data[19] = 0
        data[20] = -1
        data[21] = 0
        data[22] = -1
        data[23] = 0
        val image = TgaPreviewDecoder.decode(data)
        assertEquals(2, image.width)
        assertEquals(1, image.height)
        assertArrayEquals(intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt()), image.pixels)
    }

    @Test
    fun `decodes baseline rgb tiff strip`() {
        val entryCount = 10
        val ifdSize = 2 + entryCount * 12 + 4
        val bitsOffset = 8 + ifdSize
        val pixelsOffset = bitsOffset + 6
        val buffer = ByteBuffer.allocate(pixelsOffset + 6).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(0, 'I'.code.toByte()).put(1, 'I'.code.toByte())
        buffer.putShort(2, 42.toShort()).putInt(4, 8).putShort(8, entryCount.toShort())
        var entry = 10
        fun field(tag: Int, type: Int, count: Int, value: Int) {
            buffer.putShort(entry, tag.toShort()).putShort(entry + 2, type.toShort()).putInt(entry + 4, count).putInt(entry + 8, value)
            entry += 12
        }
        field(256, 4, 1, 2)
        field(257, 4, 1, 1)
        field(258, 3, 3, bitsOffset)
        field(259, 3, 1, 1)
        field(262, 3, 1, 2)
        field(273, 4, 1, pixelsOffset)
        field(277, 3, 1, 3)
        field(278, 4, 1, 1)
        field(279, 4, 1, 6)
        field(284, 3, 1, 1)
        buffer.putInt(entry, 0)
        repeat(3) { buffer.putShort(bitsOffset + it * 2, 8.toShort()) }
        buffer.position(pixelsOffset)
        buffer.put(byteArrayOf(-1, 0, 0, 0, -1, 0))
        val image = TiffPreviewDecoder.decode(buffer.array())
        assertArrayEquals(intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt()), image.pixels)
    }
}
