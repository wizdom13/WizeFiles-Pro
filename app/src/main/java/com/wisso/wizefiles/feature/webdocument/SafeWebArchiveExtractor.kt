// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.webdocument

import com.wisso.wizefiles.provider.archive.createArchiveRootPath
import com.wisso.wizefiles.provider.common.newInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.ArrayDeque
import java.util.Locale

object SafeWebArchiveExtractor {
    const val MAX_ENTRIES = 5_000
    const val MAX_ENTRY_BYTES = 64L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 256L * 1024 * 1024

    @Throws(IOException::class, UnsafeSavedWebDocumentException::class)
    fun extract(source: Path, outputDirectory: File): SavedWebDocumentBundle {
        val canonicalOutput = outputDirectory.canonicalFile
        if (!canonicalOutput.isDirectory && !canonicalOutput.mkdirs()) throw IOException("Unable to create saved-document cache")
        val archiveRoot = source.createArchiveRootPath()
        val queue = ArrayDeque<Pair<Path, String>>()
        queue.addLast(archiveRoot to "")
        val files = mutableListOf<File>()
        val seen = mutableSetOf<String>()
        var entryCount = 0
        var totalBytes = 0L
        try {
            while (queue.isNotEmpty()) {
                val (directory, prefix) = queue.removeFirst()
                Files.newDirectoryStream(directory).use { children ->
                    children.forEach { child ->
                        entryCount++
                        if (entryCount > MAX_ENTRIES) throw UnsafeSavedWebDocumentException("Saved bundle has too many entries")
                        val name = normalizeSegment(child.fileName.toString())
                        val relative = if (prefix.isEmpty()) name else "$prefix/$name"
                        if (!seen.add(relative.lowercase(Locale.ROOT))) throw UnsafeSavedWebDocumentException("Saved bundle has duplicate paths")
                        val attributes = Files.readAttributes(child, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                        when {
                            attributes.isDirectory -> queue.addLast(child to relative)
                            attributes.isRegularFile -> {
                                if (attributes.size() < 0 || attributes.size() > MAX_ENTRY_BYTES) throw UnsafeSavedWebDocumentException("Saved bundle entry is too large")
                                val target = File(canonicalOutput, relative).canonicalFile
                                if (!target.path.startsWith(canonicalOutput.path + File.separator)) throw UnsafeSavedWebDocumentException("Saved bundle path escapes cache")
                                target.parentFile?.mkdirs()
                                child.newInputStream().use { input ->
                                    target.outputStream().use { output ->
                                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                        var entryBytes = 0L
                                        while (true) {
                                            val read = input.read(buffer)
                                            if (read < 0) break
                                            entryBytes += read
                                            totalBytes += read
                                            if (entryBytes > MAX_ENTRY_BYTES || totalBytes > MAX_TOTAL_BYTES) throw UnsafeSavedWebDocumentException("Saved bundle exceeds the safety limit")
                                            output.write(buffer, 0, read)
                                        }
                                    }
                                }
                                files += target
                            }
                            else -> throw UnsafeSavedWebDocumentException("Saved bundle contains a special entry")
                        }
                    }
                }
            }
        } finally {
            runCatching { archiveRoot.fileSystem.close() }
        }
        return SavedWebDocumentBundle.extractedDirectory(canonicalOutput, files)
    }

    internal fun normalizeSegment(value: String): String {
        if (value.isBlank() || value == "." || value == ".." || value.indexOf('\u0000') >= 0 || value.contains('/') || value.contains('\\')) {
            throw UnsafeSavedWebDocumentException("Saved bundle path is unsafe")
        }
        return value
    }
}
