// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.fontviewer

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

data class FontMetadata(
    val format: Format,
    val familyName: String?,
    val styleName: String?,
    val fullName: String?,
    val postScriptName: String?,
    val collectionFaces: Int? = null
) {
    val preferredName: String?
        get() = fullName ?: familyName ?: postScriptName

    enum class Format {
        TTF,
        OTF,
        TTC;

        override fun toString(): String = name
    }
}

/** Minimal, bounds-checked SFNT name-table reader for TTF, OTF and TTC metadata. */
object FontMetadataReader {
    fun read(file: File): FontMetadata {
        require(file.isFile && file.canRead()) { "Font is not a readable regular file" }
        RandomAccessFile(file, "r").use { input ->
            if (input.length() < SFNT_HEADER_BYTES) throw IOException("Font header is truncated")
            return when (val signature = input.readUInt32(0)) {
                TTC_TAG -> readCollection(input)
                OTF_TAG -> readFace(input, 0, FontMetadata.Format.OTF)
                TTF_VERSION,
                TRUE_TAG -> readFace(input, 0, FontMetadata.Format.TTF)
                else -> throw IOException("Unsupported font signature: $signature")
            }
        }
    }

    private fun readCollection(input: RandomAccessFile): FontMetadata {
        if (input.length() < TTC_HEADER_BYTES) throw IOException("TTC header is truncated")
        val faces = input.readUInt32(8).toBoundedInt("TTC face count")
        if (faces !in 1..MAX_COLLECTION_FACES) throw IOException("Invalid TTC face count")
        val offsetTableEnd = TTC_HEADER_BYTES + faces.toLong() * 4L
        if (offsetTableEnd > input.length()) throw IOException("TTC offset table is truncated")
        val firstFaceOffset = input.readUInt32(TTC_HEADER_BYTES)
        val firstSignature = input.readUInt32(firstFaceOffset)
        val faceFormat = when (firstSignature) {
            OTF_TAG -> FontMetadata.Format.OTF
            TTF_VERSION,
            TRUE_TAG -> FontMetadata.Format.TTF
            else -> throw IOException("Unsupported TTC face signature")
        }
        return readFace(input, firstFaceOffset, faceFormat).copy(
            format = FontMetadata.Format.TTC,
            collectionFaces = faces
        )
    }

    private fun readFace(
        input: RandomAccessFile,
        faceOffset: Long,
        format: FontMetadata.Format
    ): FontMetadata {
        requireRange(input, faceOffset, SFNT_HEADER_BYTES)
        val tableCount = input.readUInt16(faceOffset + 4)
        if (tableCount !in 1..MAX_TABLES) throw IOException("Invalid SFNT table count")
        val directorySize = SFNT_HEADER_BYTES + tableCount.toLong() * TABLE_RECORD_BYTES
        requireRange(input, faceOffset, directorySize)

        var nameOffset = -1L
        var nameLength = -1L
        repeat(tableCount) { index ->
            val record = faceOffset + SFNT_HEADER_BYTES + index * TABLE_RECORD_BYTES
            val tag = input.readUInt32(record)
            if (tag == NAME_TAG) {
                nameOffset = input.readUInt32(record + 8)
                nameLength = input.readUInt32(record + 12)
            }
        }
        if (nameOffset < 0 || nameLength <= 0) {
            return FontMetadata(format, null, null, null, null)
        }
        requireRange(input, nameOffset, nameLength)
        return readNameTable(input, nameOffset, nameLength, format)
    }

    private fun readNameTable(
        input: RandomAccessFile,
        tableOffset: Long,
        tableLength: Long,
        format: FontMetadata.Format
    ): FontMetadata {
        if (tableLength < NAME_HEADER_BYTES) throw IOException("Font name table is truncated")
        val count = input.readUInt16(tableOffset + 2)
        if (count > MAX_NAME_RECORDS) throw IOException("Font has too many name records")
        val storageOffset = input.readUInt16(tableOffset + 4)
        val recordsEnd = NAME_HEADER_BYTES + count.toLong() * NAME_RECORD_BYTES
        if (recordsEnd > tableLength || storageOffset.toLong() > tableLength) {
            throw IOException("Font name table directory is invalid")
        }

        val best = mutableMapOf<Int, NameCandidate>()
        repeat(count) { index ->
            val record = tableOffset + NAME_HEADER_BYTES + index * NAME_RECORD_BYTES
            val platform = input.readUInt16(record)
            val encoding = input.readUInt16(record + 2)
            val language = input.readUInt16(record + 4)
            val nameId = input.readUInt16(record + 6)
            val length = input.readUInt16(record + 8)
            val relativeOffset = input.readUInt16(record + 10)
            if (nameId !in INTERESTING_NAME_IDS || length == 0) return@repeat
            val valueOffset = storageOffset.toLong() + relativeOffset
            if (valueOffset < 0 || valueOffset + length > tableLength) return@repeat
            val bytes = ByteArray(length)
            input.seek(tableOffset + valueOffset)
            input.readFully(bytes)
            val value = decodeName(bytes, platform, encoding).trim().takeIf { it.isNotEmpty() }
                ?: return@repeat
            val candidate = NameCandidate(value, score(platform, language, nameId))
            val existing = best[nameId]
            if (existing == null || candidate.score > existing.score) best[nameId] = candidate
        }

        val family = best[TYPOGRAPHIC_FAMILY_ID]?.value ?: best[FAMILY_ID]?.value
        val style = best[TYPOGRAPHIC_SUBFAMILY_ID]?.value ?: best[SUBFAMILY_ID]?.value
        return FontMetadata(
            format = format,
            familyName = family,
            styleName = style,
            fullName = best[FULL_NAME_ID]?.value,
            postScriptName = best[POSTSCRIPT_NAME_ID]?.value
        )
    }

    private fun decodeName(bytes: ByteArray, platform: Int, encoding: Int): String =
        when {
            platform == PLATFORM_UNICODE || platform == PLATFORM_WINDOWS ->
                String(bytes, StandardCharsets.UTF_16BE)
            platform == PLATFORM_MACINTOSH && encoding == 0 ->
                String(bytes, StandardCharsets.ISO_8859_1)
            else -> ""
        }.replace('\u0000', ' ')

    private fun score(platform: Int, language: Int, nameId: Int): Int {
        var score = 0
        if (platform == PLATFORM_UNICODE || platform == PLATFORM_WINDOWS) score += 100
        if (language == WINDOWS_ENGLISH_US || language == 0) score += 20
        if (nameId == TYPOGRAPHIC_FAMILY_ID || nameId == TYPOGRAPHIC_SUBFAMILY_ID) score += 10
        return score
    }

    private fun requireRange(input: RandomAccessFile, offset: Long, length: Long) {
        if (offset < 0 || length < 0 || offset > input.length() || length > input.length() - offset) {
            throw IOException("Font table range is outside the file")
        }
    }

    private fun RandomAccessFile.readUInt16(offset: Long): Int {
        requireRange(this, offset, 2)
        seek(offset)
        return readUnsignedShort()
    }

    private fun RandomAccessFile.readUInt32(offset: Long): Long {
        requireRange(this, offset, 4)
        seek(offset)
        return readInt().toLong() and 0xffff_ffffL
    }

    private fun Long.toBoundedInt(label: String): Int {
        if (this > Int.MAX_VALUE) throw IOException("$label is too large")
        return toInt()
    }

    private data class NameCandidate(val value: String, val score: Int)

    private const val SFNT_HEADER_BYTES = 12L
    private const val TABLE_RECORD_BYTES = 16L
    private const val TTC_HEADER_BYTES = 12L
    private const val NAME_HEADER_BYTES = 6L
    private const val NAME_RECORD_BYTES = 12L
    private const val MAX_TABLES = 512
    private const val MAX_COLLECTION_FACES = 256
    private const val MAX_NAME_RECORDS = 4096

    private const val FAMILY_ID = 1
    private const val SUBFAMILY_ID = 2
    private const val FULL_NAME_ID = 4
    private const val POSTSCRIPT_NAME_ID = 6
    private const val TYPOGRAPHIC_FAMILY_ID = 16
    private const val TYPOGRAPHIC_SUBFAMILY_ID = 17
    private val INTERESTING_NAME_IDS = setOf(
        FAMILY_ID,
        SUBFAMILY_ID,
        FULL_NAME_ID,
        POSTSCRIPT_NAME_ID,
        TYPOGRAPHIC_FAMILY_ID,
        TYPOGRAPHIC_SUBFAMILY_ID
    )

    private const val PLATFORM_UNICODE = 0
    private const val PLATFORM_MACINTOSH = 1
    private const val PLATFORM_WINDOWS = 3
    private const val WINDOWS_ENGLISH_US = 0x0409

    private const val TTF_VERSION = 0x0001_0000L
    private const val TRUE_TAG = 0x7472_7565L
    private const val OTF_TAG = 0x4f54_544fL
    private const val TTC_TAG = 0x7474_6366L
    private const val NAME_TAG = 0x6e61_6d65L
}
