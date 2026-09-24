package com.wisso.wizefiles.feature.apksigning

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.text.Normalizer

data class BundletoolTocInventory(
    val packageName: String,
    val describedPaths: Set<String>,
    val apkPaths: Set<String>
)

/** Minimal bounded reader for the public BuildApksResult fields needed for safe reconstruction. */
class BundletoolTocParser(
    private val maximumBytes: Int = 8 * 1024 * 1024,
    private val maximumDescriptions: Int = 4096,
    private val maximumPathLength: Int = 512
) {
    fun parse(bytes: ByteArray): BundletoolTocInventory {
        require(bytes.isNotEmpty() && bytes.size <= maximumBytes) {
            "APKS toc.pb exceeds the supported metadata size"
        }
        var packageName = ""
        val paths = linkedSetOf<String>()
        fields(bytes) { field, wire, value ->
            when {
                field == 1 && wire == WIRE_LENGTH -> parseVariant(value, paths)
                field == 3 && wire == WIRE_LENGTH -> parseApkSet(value, paths)
                field == 4 && wire == WIRE_LENGTH -> packageName = decodeUtf8(value)
            }
        }
        require(packageName.isNotBlank()) { "APKS toc.pb has no package name" }
        require(paths.isNotEmpty()) { "APKS toc.pb describes no package files" }
        return BundletoolTocInventory(
            packageName = packageName,
            describedPaths = paths,
            apkPaths = paths.filterTo(linkedSetOf()) { it.endsWith(".apk", true) }
        )
    }

    private fun parseVariant(bytes: ByteArray, paths: MutableSet<String>) {
        fields(bytes) { field, wire, value ->
            if (field == 2 && wire == WIRE_LENGTH) parseApkSet(value, paths)
        }
    }

    private fun parseApkSet(bytes: ByteArray, paths: MutableSet<String>) {
        fields(bytes) { field, wire, value ->
            if (field == 2 && wire == WIRE_LENGTH) parseDescription(value, paths)
        }
    }

    private fun parseDescription(bytes: ByteArray, paths: MutableSet<String>) {
        var path: String? = null
        fields(bytes) { field, wire, value ->
            if (field == 2 && wire == WIRE_LENGTH) path = decodeUtf8(value)
        }
        val validated = validatePath(path ?: throw IOException(
            "APKS toc.pb contains a package description without a path"
        ))
        require(paths.size < maximumDescriptions) { "APKS toc.pb has too many package paths" }
        require(paths.add(validated)) { "APKS toc.pb contains duplicate package paths" }
    }

    private inline fun fields(
        bytes: ByteArray,
        consume: (fieldNumber: Int, wireType: Int, value: ByteArray) -> Unit
    ) {
        val cursor = Cursor(bytes)
        while (!cursor.finished) {
            val tag = cursor.varint()
            val fieldNumber = (tag ushr 3).toInt()
            val wireType = (tag and 7).toInt()
            require(fieldNumber > 0) { "APKS toc.pb contains an invalid field" }
            when (wireType) {
                WIRE_VARINT -> cursor.varint()
                WIRE_FIXED64 -> cursor.skip(8)
                WIRE_LENGTH -> consume(fieldNumber, wireType, cursor.lengthDelimited())
                WIRE_FIXED32 -> cursor.skip(4)
                else -> throw IOException("APKS toc.pb uses an unsupported protobuf wire type")
            }
        }
    }

    private fun validatePath(raw: String): String {
        require(raw.isNotBlank() && raw.length <= maximumPathLength) {
            "APKS toc.pb contains an invalid package path"
        }
        require('\u0000' !in raw && '\r' !in raw && '\n' !in raw && '\\' !in raw &&
            !raw.startsWith('/') && !DRIVE_PATH.matches(raw)) {
            "APKS toc.pb contains an unsafe package path"
        }
        val path = Normalizer.normalize(raw, Normalizer.Form.NFC)
        require(path == raw) { "APKS toc.pb package path is not normalized" }
        require(path.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
            "APKS toc.pb package path uses traversal"
        }
        return path
    }

    private fun decodeUtf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()

    private class Cursor(private val bytes: ByteArray) {
        private var index = 0
        val finished: Boolean get() = index == bytes.size

        fun varint(): Long {
            var result = 0L
            for (shift in 0..63 step 7) {
                if (index >= bytes.size) throw IOException("APKS toc.pb is truncated")
                val value = bytes[index++].toInt() and 0xFF
                if (shift == 63 && value > 1) throw IOException("APKS toc.pb varint overflows")
                result = result or ((value and 0x7F).toLong() shl shift)
                if (value and 0x80 == 0) return result
            }
            throw IOException("APKS toc.pb varint is too long")
        }

        fun lengthDelimited(): ByteArray {
            val length = varint()
            if (length > Int.MAX_VALUE || length < 0) {
                throw IOException("APKS toc.pb field is too large")
            }
            val count = length.toInt()
            if (count > bytes.size - index) throw IOException("APKS toc.pb is truncated")
            return bytes.copyOfRange(index, index + count).also { index += count }
        }

        fun skip(count: Int) {
            if (count > bytes.size - index) throw IOException("APKS toc.pb is truncated")
            index += count
        }
    }

    private companion object {
        const val WIRE_VARINT = 0
        const val WIRE_FIXED64 = 1
        const val WIRE_LENGTH = 2
        const val WIRE_FIXED32 = 5
        val DRIVE_PATH = Regex("^[A-Za-z]:.*")
    }
}
