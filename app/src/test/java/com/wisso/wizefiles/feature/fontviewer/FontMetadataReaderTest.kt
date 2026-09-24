// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.fontviewer

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FontMetadataReaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `ttf name table exposes family style and full name`() {
        val file = write("sample.ttf", sfnt(TTF_TAG, 0))

        val metadata = FontMetadataReader.read(file)

        assertEquals(FontMetadata.Format.TTF, metadata.format)
        assertEquals("Example Sans", metadata.familyName)
        assertEquals("Regular", metadata.styleName)
        assertEquals("Example Sans Regular", metadata.fullName)
    }

    @Test
    fun `otf signature is reported without relying on extension`() {
        val file = write("font.bin", sfnt(OTF_TAG, 0))

        assertEquals(FontMetadata.Format.OTF, FontMetadataReader.read(file).format)
    }

    @Test
    fun `ttc reads first face metadata and face count`() {
        val faceOffset = 16
        val face = sfnt(TTF_TAG, faceOffset)
        val buffer = ByteBuffer.allocate(faceOffset + face.size).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(TTC_TAG)
        buffer.putInt(0x00010000)
        buffer.putInt(1)
        buffer.putInt(faceOffset)
        buffer.put(face)
        val file = write("sample.ttc", buffer.array())

        val metadata = FontMetadataReader.read(file)

        assertEquals(FontMetadata.Format.TTC, metadata.format)
        assertEquals(1, metadata.collectionFaces)
        assertEquals("Example Sans", metadata.familyName)
    }

    @Test
    fun `truncated font is rejected`() {
        val file = write("broken.ttf", byteArrayOf(0, 1, 0, 0))

        assertThrows(Exception::class.java) { FontMetadataReader.read(file) }
    }

    private fun sfnt(signature: Int, absoluteBaseOffset: Int): ByteArray {
        val names = listOf(
            1 to "Example Sans",
            2 to "Regular",
            4 to "Example Sans Regular"
        ).map { (id, value) -> id to value.toByteArray(StandardCharsets.UTF_16BE) }
        val stringOffset = 6 + names.size * 12
        val nameLength = stringOffset + names.sumOf { it.second.size }
        val nameTableOffset = absoluteBaseOffset + 28
        val buffer = ByteBuffer.allocate(28 + nameLength).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(signature)
        buffer.putShort(1)
        buffer.putShort(16)
        buffer.putShort(0)
        buffer.putShort(0)
        buffer.putInt(NAME_TAG)
        buffer.putInt(0)
        buffer.putInt(nameTableOffset)
        buffer.putInt(nameLength)
        buffer.putShort(0)
        buffer.putShort(names.size.toShort())
        buffer.putShort(stringOffset.toShort())
        var offset = 0
        names.forEach { (id, bytes) ->
            buffer.putShort(3)
            buffer.putShort(1)
            buffer.putShort(0x0409.toShort())
            buffer.putShort(id.toShort())
            buffer.putShort(bytes.size.toShort())
            buffer.putShort(offset.toShort())
            offset += bytes.size
        }
        names.forEach { (_, bytes) -> buffer.put(bytes) }
        return buffer.array()
    }

    private fun write(name: String, bytes: ByteArray): File =
        temporaryFolder.newFile(name).also { it.writeBytes(bytes) }

    private companion object {
        const val TTF_TAG = 0x00010000
        const val OTF_TAG = 0x4f54544f
        const val TTC_TAG = 0x74746366
        const val NAME_TAG = 0x6e616d65
    }
}
