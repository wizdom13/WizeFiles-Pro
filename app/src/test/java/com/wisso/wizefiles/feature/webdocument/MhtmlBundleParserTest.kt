package com.wisso.wizefiles.feature.webdocument

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MhtmlBundleParserTest {
    @Test
    fun `parses bounded html and base64 resources`() {
        val directory = createTempDirectory("mhtml-test-").toFile()
        try {
            val source = File(directory, "page.mhtml")
            source.writeText(
                """MIME-Version: 1.0\r
Content-Type: multipart/related; boundary="safe-boundary"; start="<root>"\r
\r
--safe-boundary\r
Content-Type: text/html; charset=utf-8\r
Content-Location: https://example.test/docs/index.html\r
Content-ID: <root>\r
\r
<html><img src="image.png"></html>\r
--safe-boundary\r
Content-Type: image/png\r
Content-Location: https://example.test/docs/image.png\r
Content-Transfer-Encoding: base64\r
\r
AQIDBA==\r
--safe-boundary--\r
""".replace("\\r\n", "\r\n")
            )
            val bundle = MhtmlBundleParser.parse(source, File(directory, "parts"))
            assertNotNull(bundle.resourceFor(SavedWebDocumentBundle.VIRTUAL_INDEX))
            val image = bundle.resourceFor("https://example.test/docs/image.png")
            assertNotNull(image)
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), image!!.file.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `decodes quoted printable soft lines`() {
        assertEquals(
            "hello world!",
            MhtmlBundleParser.decodeQuotedPrintable(
                "hello=20wor=\r\nld=21".toByteArray()
            ).toString(Charsets.UTF_8)
        )
    }
}
