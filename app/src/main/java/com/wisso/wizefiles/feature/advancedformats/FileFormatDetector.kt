package com.wisso.wizefiles.feature.advancedformats

import com.wisso.wizefiles.core.files.mime.MimeType
import java.io.InputStream
import java.io.InterruptedIOException
import java.nio.charset.StandardCharsets
import java.util.Locale

/** Bounded, side-effect-free format detection for only the file a user selected. */
object FileFormatDetector {
    const val MAX_PROBE_BYTES = 64 * 1024

    fun detect(
        fileName: String,
        mimeType: MimeType?,
        header: ByteArray
    ): DetectedFileFormat {
        require(header.size <= MAX_PROBE_BYTES) { "Header exceeds the bounded probe limit" }
        signatureFormat(header)?.let {
            return DetectedFileFormat(it, DetectionConfidence.SIGNATURE)
        }
        FileFormat.fromFileName(fileName)?.let {
            return DetectedFileFormat(it, DetectionConfidence.EXTENSION)
        }
        FileFormat.fromMimeType(mimeType)?.let {
            return DetectedFileFormat(it, DetectionConfidence.MIME)
        }
        return DetectedFileFormat(FileFormat.UNKNOWN, DetectionConfidence.UNKNOWN)
    }

    fun detect(
        fileName: String,
        mimeType: MimeType?,
        input: InputStream,
        isCancelled: () -> Boolean = { false }
    ): DetectedFileFormat = detect(fileName, mimeType, readHeader(input, isCancelled))

    fun readHeader(
        input: InputStream,
        isCancelled: () -> Boolean = { false }
    ): ByteArray {
        val header = ByteArray(MAX_PROBE_BYTES)
        var offset = 0
        while (offset < header.size) {
            if (isCancelled() || Thread.currentThread().isInterrupted) {
                throw InterruptedIOException("Format detection cancelled")
            }
            val count = input.read(header, offset, header.size - offset)
            if (count < 0) break
            if (count == 0) continue
            offset += count
        }
        return header.copyOf(offset)
    }

    private fun signatureFormat(bytes: ByteArray): FileFormat? {
        when {
            bytes.startsWith(0x25, 0x50, 0x44, 0x46, 0x2D) -> return FileFormat.PDF
            bytes.startsWith(0xFF, 0xD8, 0xFF) -> return FileFormat.JPEG
            bytes.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> return FileFormat.PNG
            bytes.asciiAt(0, "GIF87a") || bytes.asciiAt(0, "GIF89a") -> return FileFormat.GIF
            bytes.asciiAt(0, "fLaC") -> return FileFormat.FLAC
            bytes.asciiAt(0, "OggS") -> return FileFormat.OGG
            bytes.asciiAt(0, "ID3") -> return FileFormat.MP3
            bytes.asciiAt(0, "ITSF") -> return FileFormat.CHM
            bytes.asciiAt(0, "FUJIFILMCCD-RAW") -> return FileFormat.CAMERA_RAW
            bytes.size >= 68 && bytes.asciiAt(60, "BOOKMOBI") -> return FileFormat.MOBI
            bytes.startsWith(0x00, 0x00, 0x01, 0x00) || bytes.startsWith(0x00, 0x00, 0x02, 0x00) ->
                return FileFormat.ICO
            bytes.isCameraRawSignature() -> return FileFormat.CAMERA_RAW
            bytes.isTiffSignature() -> return FileFormat.TIFF
            bytes.startsWith(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) -> return FileFormat.SEVEN_ZIP
            bytes.startsWith(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07) -> return FileFormat.RAR
            bytes.startsWith(0x1F, 0x8B) -> return FileFormat.GZIP
            bytes.asciiAt(0, "BZh") -> return FileFormat.BZIP2
            bytes.startsWith(0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00) -> return FileFormat.XZ
            bytes.startsWith(0x28, 0xB5, 0x2F, 0xFD) -> return FileFormat.ZSTD
            bytes.startsWith(0x04, 0x22, 0x4D, 0x18) -> return FileFormat.LZ4
            bytes.asciiAt(0, "MSCF") -> return FileFormat.CAB
            bytes.asciiAt(0, "MSWIM") -> return FileFormat.WIM
            bytes.startsWith(0xED, 0xAB, 0xEE, 0xDB) -> return FileFormat.RPM
            bytes.asciiAt(0, "070701") || bytes.asciiAt(0, "070702") || bytes.asciiAt(0, "070707") ->
                return FileFormat.CPIO
            bytes.asciiAt(257, "ustar") -> return FileFormat.TAR
            bytes.isDebianArchive() -> return FileFormat.DEB
            bytes.startsWith(0x50, 0x4B, 0x03, 0x04) && bytes.containsAscii("application/epub+zip") ->
                return FileFormat.EPUB
            bytes.startsWith(0x50, 0x4B, 0x03, 0x04) ||
                bytes.startsWith(0x50, 0x4B, 0x05, 0x06) ||
                bytes.startsWith(0x50, 0x4B, 0x07, 0x08) -> return FileFormat.ZIP
            bytes.asciiAt(0, "vhdxfile") -> return FileFormat.VHDX
            bytes.startsWith(0x51, 0x46, 0x49, 0xFB) -> return FileFormat.QCOW
            bytes.startsWith(0x4B, 0x44, 0x4D, 0x56) || bytes.asciiAt(0, "# Disk DescriptorFile") ->
                return FileFormat.VMDK
            bytes.asciiAt(0x8001, "CD001") -> return FileFormat.ISO
            bytes.asciiAt(3, "NTFS    ") -> return FileFormat.NTFS_FILESYSTEM
            bytes.asciiAt(32, "NXSB") -> return FileFormat.APFS
            bytes.hasExtSuperblock() -> return FileFormat.EXT_FILESYSTEM
            bytes.hasFatSignature() -> return FileFormat.FAT_FILESYSTEM
            bytes.asciiAt(0, "hsqs") || bytes.asciiAt(0, "sqsh") -> return FileFormat.SQUASHFS
            bytes.startsWith(0x45, 0x3D, 0xCD, 0x28) || bytes.startsWith(0x28, 0xCD, 0x3D, 0x45) ->
                return FileFormat.CRAMFS
            bytes.isRiff("WEBP") -> return FileFormat.WEBP
            bytes.isRiff("WAVE") -> return FileFormat.WAV
            bytes.isRiff("AVI ") -> return FileFormat.AVI
        }

        val textPrefix = String(
            bytes.copyOfRange(0, minOf(bytes.size, TEXT_PROBE_BYTES)),
            StandardCharsets.ISO_8859_1
        )
            .trimStart('\uFEFF', '\u0000', ' ', '\t', '\r', '\n')
            .lowercase(Locale.ROOT)
        if (
            "mime-version:" in textPrefix &&
            ("content-type: multipart/related" in textPrefix || "content-type:multipart/related" in textPrefix)
        ) {
            return FileFormat.MHTML
        }
        if (
            textPrefix.startsWith("<!doctype html") ||
            textPrefix.startsWith("<html") ||
            (textPrefix.startsWith("<?xml") && "<html" in textPrefix)
        ) {
            return FileFormat.HTML
        }
        return null
    }

    private fun ByteArray.isCameraRawSignature(): Boolean =
        (isTiffSignature() && asciiAt(8, "CR\u0002")) ||
            asciiAt(0, "IIRO") || asciiAt(0, "IIRS") ||
            startsWith(0x49, 0x49, 0x55, 0x00)

    private fun ByteArray.isTiffSignature(): Boolean =
        startsWith(0x49, 0x49, 0x2A, 0x00) || startsWith(0x4D, 0x4D, 0x00, 0x2A) ||
            startsWith(0x49, 0x49, 0x2B, 0x00) || startsWith(0x4D, 0x4D, 0x00, 0x2B)

    private fun ByteArray.isRiff(formType: String): Boolean = asciiAt(0, "RIFF") && asciiAt(8, formType)

    private fun ByteArray.isDebianArchive(): Boolean =
        asciiAt(0, "!<arch>\n") && containsAscii("debian-binary")

    private fun ByteArray.hasExtSuperblock(): Boolean =
        size > EXT_MAGIC_OFFSET + 1 &&
            (this[EXT_MAGIC_OFFSET].toInt() and 0xFF) == 0x53 &&
            (this[EXT_MAGIC_OFFSET + 1].toInt() and 0xFF) == 0xEF

    private fun ByteArray.hasFatSignature(): Boolean =
        asciiAt(54, "FAT12   ") || asciiAt(54, "FAT16   ") || asciiAt(82, "FAT32   ")

    private fun ByteArray.asciiAt(offset: Int, value: String): Boolean {
        if (offset < 0 || size - offset < value.length) return false
        return value.indices.all { (this[offset + it].toInt() and 0xFF) == value[it].code }
    }

    private fun ByteArray.containsAscii(value: String): Boolean {
        if (value.isEmpty() || value.length > size) return false
        return (0..size - value.length).any { asciiAt(it, value) }
    }

    private fun ByteArray.startsWith(vararg expected: Int): Boolean =
        size >= expected.size && expected.indices.all { (this[it].toInt() and 0xFF) == expected[it] }

    private const val TEXT_PROBE_BYTES = 4096
    private const val EXT_MAGIC_OFFSET = 1024 + 56
}
