// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.advancedformats.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.wisso.wizefiles.feature.advancedformats.FileFormat
import com.wisso.wizefiles.provider.common.newInputStream
import com.wisso.wizefiles.provider.common.size
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Path
import java.util.zip.InflaterInputStream

object AdvancedImagePreviewDecoder {
    const val MAX_SOURCE_BYTES = 256L * 1024 * 1024
    const val MAX_BUFFERED_SOURCE_BYTES = 64L * 1024 * 1024
    const val MAX_PIXELS = 8_000_000L

    fun supports(fileName: String): Boolean = FileFormat.fromFileName(fileName) in SUPPORTED_FORMATS

    @Throws(IOException::class)
    fun decode(path: Path, fileName: String): Bitmap {
        val format = FileFormat.fromFileName(fileName) ?: throw IOException("Unknown image format")
        if (format !in SUPPORTED_FORMATS) throw IOException("Unsupported image format")
        val sourceSize = path.size()
        if (sourceSize !in 1..MAX_SOURCE_BYTES) throw IOException("Image exceeds the safety limit")
        decodeWithPlatform(path)?.let { return applyExifOrientation(path, it, format) }
        val decoded = when (format) {
            FileFormat.TGA -> TgaPreviewDecoder.decode(readBounded(path)).toBitmap()
            FileFormat.ICO -> IcoPreviewDecoder.decode(readBounded(path))
            FileFormat.TIFF -> TiffPreviewDecoder.decode(readBounded(path)).toBitmap()
            FileFormat.CAMERA_RAW -> readExifPreview(path)
                ?: throw IOException("RAW file has no supported embedded preview")
            else -> throw IOException("Unsupported image format")
        }
        return applyExifOrientation(path, decoded, format)
    }

    private fun decodeWithPlatform(path: Path): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        path.newInputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while ((bounds.outWidth / sampleSize).toLong() * (bounds.outHeight / sampleSize) > MAX_PIXELS) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = path.newInputStream().use { BitmapFactory.decodeStream(it, null, options) }
        if (decoded != null && decoded.width.toLong() * decoded.height <= MAX_PIXELS) return decoded
        decoded?.recycle()
        return null
    }

    private fun readExifPreview(path: Path): Bitmap? = path.newInputStream().use { input ->
        ExifInterface(input).thumbnailBitmap
    }?.let { bitmap ->
        if (bitmap.width.toLong() * bitmap.height <= MAX_PIXELS) bitmap
        else null.also { bitmap.recycle() }
    }

    private fun applyExifOrientation(path: Path, bitmap: Bitmap, format: FileFormat): Bitmap {
        if (format == FileFormat.ICO || format == FileFormat.TGA) return bitmap
        val orientation = runCatching {
            path.newInputStream().use { input ->
                ExifInterface(input).let { it.rotationDegrees to it.isFlipped }
            }
        }.getOrNull() ?: return bitmap
        if (orientation.first == 0 && !orientation.second) return bitmap
        val matrix = Matrix().apply {
            if (orientation.second) postScale(-1f, 1f)
            if (orientation.first != 0) postRotate(orientation.first.toFloat())
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }

    private fun readBounded(path: Path): ByteArray {
        val output = ByteArrayOutputStream()
        path.newInputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (output.size().toLong() + read > MAX_BUFFERED_SOURCE_BYTES) {
                    throw IOException("Image exceeds the buffered decoder limit")
                }
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    }

    internal fun RasterImage.toBitmap(): Bitmap =
        Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)

    private val SUPPORTED_FORMATS = setOf(FileFormat.ICO, FileFormat.TIFF, FileFormat.CAMERA_RAW, FileFormat.TGA)
}

internal data class RasterImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(width > 0 && height > 0 && width.toLong() * height <= AdvancedImagePreviewDecoder.MAX_PIXELS)
        require(pixels.size == width * height)
    }
}

internal object TgaPreviewDecoder {
    @Throws(IOException::class)
    fun decode(data: ByteArray): RasterImage {
        if (data.size < 18) throw IOException("TGA header is truncated")
        val idLength = u8(data, 0)
        if (u8(data, 1) != 0) throw IOException("Color-mapped TGA is unsupported")
        val imageType = u8(data, 2)
        val width = u16(data, 12)
        val height = u16(data, 14)
        val bits = u8(data, 16)
        val descriptor = u8(data, 17)
        if (width <= 0 || height <= 0 || width.toLong() * height > AdvancedImagePreviewDecoder.MAX_PIXELS) {
            throw IOException("TGA dimensions exceed the safety limit")
        }
        val grayscale = imageType == 3 || imageType == 11
        val rle = imageType == 10 || imageType == 11
        if (imageType !in setOf(2, 3, 10, 11)) throw IOException("Unsupported TGA image type")
        if ((grayscale && bits != 8) || (!grayscale && bits !in setOf(16, 24, 32))) {
            throw IOException("Unsupported TGA pixel depth")
        }
        var offset = 18 + idLength
        val pixels = IntArray(width * height)
        var written = 0
        fun writePixel(pixel: Int) {
            if (written >= pixels.size) throw IOException("TGA RLE exceeds image dimensions")
            val streamX = written % width
            val streamY = written / width
            val x = if (descriptor and 0x10 != 0) width - 1 - streamX else streamX
            val y = if (descriptor and 0x20 != 0) streamY else height - 1 - streamY
            pixels[y * width + x] = pixel
            written++
        }
        if (!rle) {
            while (written < pixels.size) {
                val result = readPixel(data, offset, bits, grayscale, descriptor and 0x0F != 0)
                offset = result.second
                writePixel(result.first)
            }
        } else {
            while (written < pixels.size) {
                if (offset >= data.size) throw IOException("TGA RLE packet is truncated")
                val header = u8(data, offset++)
                val count = (header and 0x7F) + 1
                if (header and 0x80 != 0) {
                    val result = readPixel(data, offset, bits, grayscale, descriptor and 0x0F != 0)
                    offset = result.second
                    repeat(count) { writePixel(result.first) }
                } else {
                    repeat(count) {
                        val result = readPixel(data, offset, bits, grayscale, descriptor and 0x0F != 0)
                        offset = result.second
                        writePixel(result.first)
                    }
                }
            }
        }
        return RasterImage(width, height, pixels)
    }

    private fun readPixel(
        data: ByteArray,
        start: Int,
        bits: Int,
        grayscale: Boolean,
        hasAttributeBits: Boolean
    ): Pair<Int, Int> {
        val bytes = bits / 8
        if (start < 0 || start + bytes > data.size) throw IOException("TGA pixels are truncated")
        if (grayscale) {
            val value = u8(data, start)
            return (0xFF000000.toInt() or (value shl 16) or (value shl 8) or value) to start + 1
        }
        if (bits == 16) {
            val value = u16(data, start)
            val r = ((value ushr 10) and 0x1F) * 255 / 31
            val g = ((value ushr 5) and 0x1F) * 255 / 31
            val b = (value and 0x1F) * 255 / 31
            val a = if (!hasAttributeBits || value and 0x8000 != 0) 255 else 0
            return ((a shl 24) or (r shl 16) or (g shl 8) or b) to start + 2
        }
        val b = u8(data, start)
        val g = u8(data, start + 1)
        val r = u8(data, start + 2)
        val a = if (bits == 32) u8(data, start + 3) else 255
        return ((a shl 24) or (r shl 16) or (g shl 8) or b) to start + bytes
    }
}

internal object IcoPreviewDecoder {
    @Throws(IOException::class)
    fun decode(data: ByteArray): Bitmap {
        if (data.size < 6 || u16(data, 0) != 0 || u16(data, 2) !in setOf(1, 2)) {
            throw IOException("ICO header is invalid")
        }
        val count = u16(data, 4)
        if (count !in 1..256 || 6 + count * 16 > data.size) throw IOException("ICO directory is invalid")
        val entries = (0 until count).mapNotNull { index ->
            val offset = 6 + index * 16
            val width = u8(data, offset).let { if (it == 0) 256 else it }
            val height = u8(data, offset + 1).let { if (it == 0) 256 else it }
            val size = u32(data, offset + 8)
            val imageOffset = u32(data, offset + 12)
            if (size <= 0 || imageOffset < 0 || imageOffset.toLong() + size > data.size) null
            else IcoEntry(width, height, size, imageOffset)
        }.sortedByDescending { it.width.toLong() * it.height }
        for (entry in entries) {
            val bitmap = if (isPng(data, entry.offset)) {
                decodePng(data, entry)
            } else {
                decodeDib(data, entry)
            }
            if (bitmap != null && bitmap.width.toLong() * bitmap.height <= AdvancedImagePreviewDecoder.MAX_PIXELS) return bitmap
        }
        throw IOException("ICO has no supported image entry")
    }

    private fun decodeDib(data: ByteArray, entry: IcoEntry): Bitmap? {
        val start = entry.offset
        if (start + 40 > data.size) return null
        val headerSize = u32(data, start)
        val width = s32(data, start + 4)
        val storedHeight = s32(data, start + 8)
        val bits = u16(data, start + 14)
        val compression = u32(data, start + 16)
        if (headerSize < 40 || width <= 0 || storedHeight == 0 || bits !in setOf(24, 32) || compression != 0) return null
        val height = kotlin.math.abs(storedHeight) / 2
        if (height <= 0 || width.toLong() * height > AdvancedImagePreviewDecoder.MAX_PIXELS) return null
        val rowBytes = (((width.toLong() * bits + 31) / 32) * 4).toInt()
        val pixelsStartLong = start.toLong() + headerSize
        val maskStart = pixelsStartLong + rowBytes.toLong() * height
        val maskRowBytes = ((width + 31) / 32) * 4
        val maskEnd = maskStart + maskRowBytes.toLong() * height
        val entryEnd = start.toLong() + entry.size
        if (pixelsStartLong > Int.MAX_VALUE || maskEnd > entryEnd || maskEnd > data.size) return null
        val pixelsStart = pixelsStartLong.toInt()
        var hasAlpha = false
        if (bits == 32) {
            for (y in 0 until height) for (x in 0 until width) {
                if (u8(data, pixelsStart + y * rowBytes + x * 4 + 3) != 0) hasAlpha = true
            }
        }
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val sourceY = if (storedHeight < 0) y else height - 1 - y
            for (x in 0 until width) {
                val position = pixelsStart + sourceY * rowBytes + x * (bits / 8)
                val b = u8(data, position)
                val g = u8(data, position + 1)
                val r = u8(data, position + 2)
                var a = if (bits == 32 && hasAlpha) u8(data, position + 3) else 255
                val maskPosition = maskStart.toInt() + sourceY * maskRowBytes + x / 8
                if (maskPosition < data.size && u8(data, maskPosition) and (0x80 ushr (x % 8)) != 0) a = 0
                pixels[y * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return RasterImage(width, height, pixels).let { AdvancedImagePreviewDecoder.run { it.toBitmap() } }
    }

    private fun decodePng(data: ByteArray, entry: IcoEntry): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, entry.offset, entry.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (
            (bounds.outWidth / sampleSize).toLong() * (bounds.outHeight / sampleSize) >
            AdvancedImagePreviewDecoder.MAX_PIXELS
        ) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(data, entry.offset, entry.size, options)
        if (
            bitmap != null &&
            bitmap.width.toLong() * bitmap.height <= AdvancedImagePreviewDecoder.MAX_PIXELS
        ) {
            return bitmap
        }
        bitmap?.recycle()
        return null
    }

    private fun isPng(data: ByteArray, offset: Int): Boolean =
        offset >= 0 && offset + 8 <= data.size && data.copyOfRange(offset, offset + 8).contentEquals(PNG_SIGNATURE)

    private data class IcoEntry(val width: Int, val height: Int, val size: Int, val offset: Int)
    private val PNG_SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
}

internal object TiffPreviewDecoder {
    @Throws(IOException::class)
    fun decode(data: ByteArray): RasterImage {
        if (data.size < 8) throw IOException("TIFF header is truncated")
        val reader = TiffReader(data)
        val littleEndian = when (data.take(2).toByteArray().toString(Charsets.US_ASCII)) {
            "II" -> true
            "MM" -> false
            else -> throw IOException("TIFF byte order is invalid")
        }
        reader.littleEndian = littleEndian
        if (reader.u16(2) != 42) throw IOException("BigTIFF and invalid TIFF files are unsupported")
        val ifdOffset = reader.u32(4)
        val entries = reader.ifd(ifdOffset)
        val width = reader.first(entries, 256).toInt()
        val height = reader.first(entries, 257).toInt()
        val compression = reader.first(entries, 259, 1).toInt()
        val photometric = reader.first(entries, 262).toInt()
        val samples = reader.first(entries, 277, 1).toInt()
        val rowsPerStrip = reader.first(entries, 278, height.toLong()).toInt()
        val planar = reader.first(entries, 284, 1).toInt()
        val predictor = reader.first(entries, 317, 1).toInt()
        val bits = reader.values(entries[258] ?: throw IOException("TIFF bits are missing"))
        val stripOffsets = reader.values(entries[273] ?: throw IOException("TIFF strips are missing"))
        val stripSizes = reader.values(entries[279] ?: throw IOException("TIFF strip sizes are missing"))
        if (width <= 0 || height <= 0 || width.toLong() * height > AdvancedImagePreviewDecoder.MAX_PIXELS || rowsPerStrip <= 0) {
            throw IOException("TIFF dimensions exceed the safety limit")
        }
        if (
            planar != 1 || predictor != 1 || samples !in 1..4 ||
            bits.size !in setOf(1, samples) || bits.any { it != 8L }
        ) {
            throw IOException("Unsupported TIFF sample layout")
        }
        if (stripOffsets.size != stripSizes.size || stripOffsets.isEmpty()) throw IOException("TIFF strip table is invalid")
        val pixels = IntArray(width * height)
        var row = 0
        stripOffsets.indices.forEach { index ->
            if (row >= height) return@forEach
            val rows = minOf(rowsPerStrip, height - row)
            val expected = Math.multiplyExact(Math.multiplyExact(width, rows), samples)
            val encoded = reader.slice(stripOffsets[index], stripSizes[index])
            val decoded = when (compression) {
                1 -> encoded
                8, 32946 -> InflaterInputStream(ByteArrayInputStream(encoded)).use { it.readBounded(expected) }
                32773 -> decodePackBits(encoded, expected)
                else -> throw IOException("Unsupported TIFF compression")
            }
            if (decoded.size < expected) throw IOException("TIFF strip is truncated")
            var input = 0
            repeat(rows) { stripRow ->
                repeat(width) { x ->
                    val pixel = when (photometric) {
                        0, 1 -> {
                            val raw = u8(decoded, input)
                            val gray = if (photometric == 0) 255 - raw else raw
                            val alpha = if (samples >= 2) u8(decoded, input + 1) else 255
                            (alpha shl 24) or (gray shl 16) or (gray shl 8) or gray
                        }
                        2 -> {
                            if (samples < 3) throw IOException("TIFF RGB samples are missing")
                            val red = u8(decoded, input)
                            val green = u8(decoded, input + 1)
                            val blue = u8(decoded, input + 2)
                            val alpha = if (samples >= 4) u8(decoded, input + 3) else 255
                            (alpha shl 24) or (red shl 16) or (green shl 8) or blue
                        }
                        else -> throw IOException("Unsupported TIFF photometric interpretation")
                    }
                    pixels[(row + stripRow) * width + x] = pixel
                    input += samples
                }
            }
            row += rows
        }
        if (row < height) throw IOException("TIFF image rows are missing")
        return RasterImage(width, height, pixels)
    }

    private fun decodePackBits(input: ByteArray, expected: Int): ByteArray {
        val output = ByteArrayOutputStream(expected)
        var index = 0
        while (index < input.size && output.size() < expected) {
            val header = input[index++].toInt()
            when {
                header in 0..127 -> {
                    val count = header + 1
                    if (index + count > input.size) throw IOException("TIFF PackBits data is truncated")
                    output.write(input, index, count)
                    index += count
                }
                header in -127..-1 -> {
                    if (index >= input.size) throw IOException("TIFF PackBits run is truncated")
                    repeat(1 - header) { output.write(input[index].toInt()) }
                    index++
                }
            }
            if (output.size() > expected) throw IOException("TIFF PackBits data exceeds strip size")
        }
        return output.toByteArray()
    }

    private class TiffReader(private val data: ByteArray) {
        var littleEndian = true
        fun u16(offset: Int): Int {
            requireRange(offset, 2)
            return if (littleEndian) u8(data, offset) or (u8(data, offset + 1) shl 8)
            else (u8(data, offset) shl 8) or u8(data, offset + 1)
        }
        fun u32(offset: Int): Int {
            requireRange(offset, 4)
            val value = if (littleEndian) {
                u8(data, offset).toLong() or (u8(data, offset + 1).toLong() shl 8) or
                    (u8(data, offset + 2).toLong() shl 16) or (u8(data, offset + 3).toLong() shl 24)
            } else {
                (u8(data, offset).toLong() shl 24) or (u8(data, offset + 1).toLong() shl 16) or
                    (u8(data, offset + 2).toLong() shl 8) or u8(data, offset + 3).toLong()
            }
            if (value > Int.MAX_VALUE) throw IOException("TIFF offset exceeds the safety limit")
            return value.toInt()
        }
        fun ifd(offset: Int): Map<Int, Entry> {
            val count = u16(offset)
            if (count !in 1..4_096) throw IOException("TIFF IFD is invalid")
            requireRange(offset + 2, count * 12 + 4)
            return (0 until count).associate { index ->
                val entryOffset = offset + 2 + index * 12
                val tag = u16(entryOffset)
                tag to Entry(u16(entryOffset + 2), u32(entryOffset + 4), entryOffset + 8)
            }
        }
        fun first(entries: Map<Int, Entry>, tag: Int, default: Long? = null): Long =
            entries[tag]?.let(::values)?.firstOrNull() ?: default ?: throw IOException("TIFF tag $tag is missing")
        fun values(entry: Entry): LongArray {
            val unit = when (entry.type) { 1 -> 1; 3 -> 2; 4 -> 4; else -> throw IOException("Unsupported TIFF field type") }
            if (entry.count !in 1..1_000_000) throw IOException("TIFF field count is invalid")
            val bytes = Math.multiplyExact(entry.count, unit)
            val start = if (bytes <= 4) entry.valueOffset else u32(entry.valueOffset)
            requireRange(start, bytes)
            return LongArray(entry.count) { index ->
                when (entry.type) {
                    1 -> u8(data, start + index).toLong()
                    3 -> u16(start + index * 2).toLong()
                    4 -> u32(start + index * 4).toLong()
                    else -> error("unreachable")
                }
            }
        }
        fun slice(offset: Long, size: Long): ByteArray {
            if (offset < 0 || size < 0 || offset + size > data.size || size > Int.MAX_VALUE) throw IOException("TIFF strip range is invalid")
            return data.copyOfRange(offset.toInt(), (offset + size).toInt())
        }
        private fun requireRange(offset: Int, size: Int) {
            if (offset < 0 || size < 0 || offset.toLong() + size > data.size) throw IOException("TIFF structure is truncated")
        }
        data class Entry(val type: Int, val count: Int, val valueOffset: Int)
    }
}

private fun InflaterInputStream.readBounded(expected: Int): ByteArray {
    val output = ByteArrayOutputStream(expected)
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        if (output.size().toLong() + read > expected) throw IOException("TIFF strip expands beyond its dimensions")
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private fun u8(data: ByteArray, offset: Int): Int {
    if (offset !in data.indices) throw IOException("Image data is truncated")
    return data[offset].toInt() and 0xFF
}
private fun u16(data: ByteArray, offset: Int): Int = u8(data, offset) or (u8(data, offset + 1) shl 8)
private fun u32(data: ByteArray, offset: Int): Int {
    val value = u8(data, offset).toLong() or (u8(data, offset + 1).toLong() shl 8) or
        (u8(data, offset + 2).toLong() shl 16) or (u8(data, offset + 3).toLong() shl 24)
    if (value > Int.MAX_VALUE) throw IOException("Image offset exceeds the safety limit")
    return value.toInt()
}
private fun s32(data: ByteArray, offset: Int): Int =
    u8(data, offset) or (u8(data, offset + 1) shl 8) or (u8(data, offset + 2) shl 16) or (data[offset + 3].toInt() shl 24)
