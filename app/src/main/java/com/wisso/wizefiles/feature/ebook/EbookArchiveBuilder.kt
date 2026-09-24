package com.wisso.wizefiles.feature.ebook

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Packages libmobi's fixed-name resource bundle into a standards-compliant local EPUB. */
object EbookArchiveBuilder {
    @Throws(IOException::class)
    fun createEpub(bundleDirectory: File, outputFile: File): File {
        val canonicalRoot = bundleDirectory.canonicalFile
        val mimetype = File(canonicalRoot, "mimetype")
        val container = File(canonicalRoot, "META-INF/container.xml")
        val oebps = File(canonicalRoot, "OEBPS")
        if (!mimetype.isFile || !container.isFile || !oebps.isDirectory) {
            throw IOException("Converted EPUB bundle is incomplete")
        }
        val files = buildList {
            add(container)
            val children = oebps.listFiles()?.sortedBy { it.name }
                ?: throw IOException("Unable to enumerate converted EPUB")
            addAll(children)
        }
        if (files.size + 1 > EpubSafetyValidator.MAX_ENTRIES) {
            throw IOException("Converted EPUB contains too many resources")
        }

        ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { zip ->
            val mimeBytes = EpubSafetyValidator.EPUB_MIME_TYPE.toByteArray(Charsets.US_ASCII)
            val crc = CRC32().apply { update(mimeBytes) }
            zip.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimeBytes.size.toLong()
                compressedSize = mimeBytes.size.toLong()
                this.crc = crc.value
                time = 0
            })
            zip.write(mimeBytes)
            zip.closeEntry()

            files.forEach { file ->
                if (!file.isFile || file.canonicalPath.let { it != canonicalRoot.path && !it.startsWith(canonicalRoot.path + File.separator) }) {
                    throw IOException("Converted EPUB resource escapes its bundle")
                }
                if (file.length() > EpubSafetyValidator.MAX_ENTRY_BYTES) {
                    throw IOException("Converted EPUB resource is too large")
                }
                val relative = file.relativeTo(canonicalRoot).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry(relative).apply { time = 0 })
                BufferedInputStream(FileInputStream(file)).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        EpubSafetyValidator.validate(outputFile)
        return outputFile
    }
}
