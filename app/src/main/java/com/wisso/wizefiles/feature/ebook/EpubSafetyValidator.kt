package com.wisso.wizefiles.feature.ebook

import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/** Bounded EPUB container checks performed before Readium sees any publication resource. */
object EpubSafetyValidator {
    const val MAX_SOURCE_BYTES = 512L * 1024L * 1024L
    const val MAX_EXPANDED_BYTES = 512L * 1024L * 1024L
    const val MAX_ENTRY_BYTES = 64L * 1024L * 1024L
    const val MAX_ENTRIES = 10_000
    const val MAX_COMPRESSION_RATIO = 100L

    @Throws(IOException::class)
    fun validate(file: File) {
        if (!file.isFile || file.length() !in 1..MAX_SOURCE_BYTES) {
            throw UnsafeEpubException("EPUB source is empty or too large")
        }
        ZipFile(file).use { zip ->
            val names = HashSet<String>()
            var count = 0
            var expandedBytes = 0L
            var mimetypeFound = false
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                count++
                if (count > MAX_ENTRIES) throw UnsafeEpubException("Too many EPUB entries")
                val normalizedName = validateName(entry.name, entry.isDirectory)
                if (!names.add(normalizedName)) throw UnsafeEpubException("Duplicate EPUB entry")

                val size = entry.size
                val compressedSize = entry.compressedSize
                if (size < 0 || compressedSize < 0 || size > MAX_ENTRY_BYTES) {
                    throw UnsafeEpubException("Invalid EPUB entry size")
                }
                if (size > 0 && (compressedSize == 0L || size > compressedSize * MAX_COMPRESSION_RATIO)) {
                    throw UnsafeEpubException("Suspicious EPUB compression ratio")
                }
                if (expandedBytes > MAX_EXPANDED_BYTES - size) {
                    throw UnsafeEpubException("EPUB expands beyond its safety limit")
                }
                expandedBytes += size

                if (entry.name == "mimetype") {
                    if (entry.isDirectory || size > 64) throw UnsafeEpubException("Invalid EPUB mimetype")
                    val value = zip.getInputStream(entry).use { String(it.readBytes(), Charsets.US_ASCII) }
                    if (value != EPUB_MIME_TYPE) throw UnsafeEpubException("Invalid EPUB mimetype")
                    mimetypeFound = true
                }
            }
            if (!mimetypeFound) throw UnsafeEpubException("EPUB mimetype is missing")
        }
    }

    private fun validateName(name: String, isDirectory: Boolean): String {
        val normalized = if (isDirectory && name.endsWith('/')) name.dropLast(1) else name
        if (
            normalized.isBlank() || normalized.startsWith('/') || '\\' in normalized ||
            '\u0000' in normalized || WINDOWS_ABSOLUTE.matches(normalized)
        ) {
            throw UnsafeEpubException("Invalid EPUB path")
        }
        val segments = normalized.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." }) {
            throw UnsafeEpubException("EPUB path traversal is not allowed")
        }
        return normalized
    }

    const val EPUB_MIME_TYPE = "application/epub+zip"
    private val WINDOWS_ABSOLUTE = Regex("[A-Za-z]:/.*")
}

class UnsafeEpubException(message: String) : IOException(message)
