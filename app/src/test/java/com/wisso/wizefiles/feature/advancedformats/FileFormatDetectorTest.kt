package com.wisso.wizefiles.feature.advancedformats

import com.wisso.wizefiles.core.files.mime.MimeType
import java.io.ByteArrayInputStream
import java.io.InterruptedIOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FileFormatDetectorTest {
    @Test
    fun `signature wins over misleading extension and mime`() {
        val result = FileFormatDetector.detect(
            "photo.jpg",
            MimeType("image/jpeg"),
            "%PDF-1.7".toByteArray()
        )

        assertEquals(FileFormat.PDF, result.format)
        assertEquals(DetectionConfidence.SIGNATURE, result.confidence)
    }

    @Test
    fun `compound and split extensions are normalized`() {
        mapOf(
            "backup.TAR.GZ" to FileFormat.TAR_GZIP,
            "archive.7z.001" to FileFormat.SEVEN_ZIP,
            "archive.zip.002" to FileFormat.ZIP,
            "archive.part12.rar" to FileFormat.RAR,
            "archive.r04" to FileFormat.RAR
        ).forEach { (name, expected) ->
            val result = FileFormatDetector.detect(name, MimeType.GENERIC, byteArrayOf())
            assertEquals(name, expected, result.format)
            assertEquals(name, DetectionConfidence.EXTENSION, result.confidence)
        }
    }

    @Test
    fun `specialized format signatures are detected`() {
        val mobi = ByteArray(68).apply { "BOOKMOBI".toByteArray().copyInto(this, 60) }
        val iso = ByteArray(0x8006).apply { "CD001".toByteArray().copyInto(this, 0x8001) }
        val ext = ByteArray(1082).apply {
            this[1080] = 0x53
            this[1081] = 0xEF.toByte()
        }
        mapOf(
            FileFormat.MOBI to mobi,
            FileFormat.ISO to iso,
            FileFormat.EXT_FILESYSTEM to ext,
            FileFormat.CHM to "ITSF".toByteArray(),
            FileFormat.VHDX to "vhdxfile".toByteArray(),
            FileFormat.QCOW to byteArrayOf(0x51, 0x46, 0x49, 0xFB.toByte())
        ).forEach { (expected, bytes) ->
            assertEquals(
                expected,
                FileFormatDetector.detect("unknown.bin", MimeType.GENERIC, bytes).format
            )
        }
    }

    @Test
    fun `html and mhtml content are detected without trusting their names`() {
        val html = "  <!DOCTYPE html><html><body>hello</body></html>".toByteArray()
        val mhtml = (
            "MIME-Version: 1.0\r\n" +
                "Content-Type: multipart/related; boundary=sample\r\n"
            ).toByteArray()

        assertEquals(
            FileFormat.HTML,
            FileFormatDetector.detect("page.bin", MimeType.GENERIC, html).format
        )
        assertEquals(
            FileFormat.MHTML,
            FileFormatDetector.detect("snapshot.bin", MimeType.GENERIC, mhtml).format
        )
    }

    @Test
    fun `generic mime remains unknown when no signature or extension matches`() {
        val result = FileFormatDetector.detect("README", MimeType.GENERIC, byteArrayOf(1, 2, 3))

        assertEquals(FileFormat.UNKNOWN, result.format)
        assertEquals(DetectionConfidence.UNKNOWN, result.confidence)
    }

    @Test
    fun `stream probe is capped and leaves later bytes unread`() {
        val source = ByteArray(FileFormatDetector.MAX_PROBE_BYTES + 10) { 0x41 }
        val input = ByteArrayInputStream(source)

        val header = FileFormatDetector.readHeader(input)

        assertEquals(FileFormatDetector.MAX_PROBE_BYTES, header.size)
        assertEquals(10, input.available())
    }

    @Test
    fun `stream probe responds to cancellation`() {
        assertThrows(InterruptedIOException::class.java) {
            FileFormatDetector.readHeader(ByteArrayInputStream(byteArrayOf(1))) { true }
        }
    }

    @Test
    fun `callers cannot bypass the bounded header contract`() {
        assertThrows(IllegalArgumentException::class.java) {
            FileFormatDetector.detect(
                "oversized.bin",
                MimeType.GENERIC,
                ByteArray(FileFormatDetector.MAX_PROBE_BYTES + 1)
            )
        }
    }
}
