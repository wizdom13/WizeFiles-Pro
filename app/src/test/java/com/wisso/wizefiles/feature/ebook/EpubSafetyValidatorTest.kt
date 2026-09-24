package com.wisso.wizefiles.feature.ebook

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EpubSafetyValidatorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `accepts a small bounded epub`() {
        val epub = zip(
            "mimetype" to EpubSafetyValidator.EPUB_MIME_TYPE.toByteArray(),
            "OEBPS/" to byteArrayOf(),
            "META-INF/container.xml" to "<container/>".toByteArray(),
            "OEBPS/chapter.xhtml" to "<html/>".toByteArray()
        )
        EpubSafetyValidator.validate(epub)
    }

    @Test
    fun `rejects traversal entries`() {
        val epub = zip(
            "mimetype" to EpubSafetyValidator.EPUB_MIME_TYPE.toByteArray(),
            "../outside" to byteArrayOf(1)
        )
        assertThrows(UnsafeEpubException::class.java) { EpubSafetyValidator.validate(epub) }
    }

    @Test
    fun `rejects excessive compression ratio`() {
        val epub = zip(
            "mimetype" to EpubSafetyValidator.EPUB_MIME_TYPE.toByteArray(),
            "OEBPS/bomb.xhtml" to ByteArray(1024 * 1024)
        )
        assertThrows(UnsafeEpubException::class.java) { EpubSafetyValidator.validate(epub) }
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): File {
        val file = temporaryFolder.newFile("book-${System.nanoTime()}.epub")
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return file
    }
}
