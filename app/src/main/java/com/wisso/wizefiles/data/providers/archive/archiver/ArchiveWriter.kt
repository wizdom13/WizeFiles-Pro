// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.archive.archiver

import java.nio.channels.SeekableByteChannel
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import com.wisso.wizefiles.provider.common.PosixFileAttributes
import com.wisso.wizefiles.provider.common.PosixFileMode
import com.wisso.wizefiles.provider.common.PosixFileType
import com.wisso.wizefiles.provider.common.copyTo
import com.wisso.wizefiles.provider.common.getLastModifiedTime
import com.wisso.wizefiles.provider.common.newInputStream
import com.wisso.wizefiles.provider.common.readAttributes
import com.wisso.wizefiles.provider.common.readSymbolicLinkByteString
import com.wisso.wizefiles.provider.common.size
import java.io.Closeable
import java.io.IOException
import java.io.InputStream

class ArchiveWriter @Throws(IOException::class) constructor(
    channel: SeekableByteChannel,
    format: Int,
    filter: Int,
    password: String?
) : Closeable {
    private val archive = WriteArchive(channel, format, filter, password)

    @Throws(IOException::class)
    fun write(file: Path, entryName: Path, intervalMillis: Long, listener: ((Long) -> Unit)?) {
        val name = entryName.toString()
        val lastModifiedTime = file.getLastModifiedTime(LinkOption.NOFOLLOW_LINKS)
        val lastAccessTime = null
        val creationTime = null
        val attributes = file.readAttributes(
            BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS
        )
        val type = when {
            attributes is PosixFileAttributes -> attributes.type()
            attributes.isDirectory -> PosixFileType.DIRECTORY
            attributes.isSymbolicLink -> PosixFileType.SYMBOLIC_LINK
            else -> PosixFileType.REGULAR_FILE
        }
        val size = file.size(LinkOption.NOFOLLOW_LINKS)
        val posixAttributes = attributes as? PosixFileAttributes
        val owner = posixAttributes?.owner()
        val group = posixAttributes?.group()
        val mode = posixAttributes?.mode() ?: when {
            attributes.isDirectory -> PosixFileMode.DIRECTORY_DEFAULT
            attributes.isSymbolicLink -> PosixFileMode.SYMBOLIC_LINK_DEFAULT
            else -> PosixFileMode.FILE_DEFAULT
        }
        val symbolicLinkTarget = if (attributes.isSymbolicLink) {
            file.readSymbolicLinkByteString().toString()
        } else {
            null
        }
        archive.Entry(
            name, lastModifiedTime, lastAccessTime, creationTime, type, size, owner, group, mode,
            symbolicLinkTarget
        ).use { archive.writeEntry(it) }
        if (type == PosixFileType.REGULAR_FILE) {
            file.newInputStream(LinkOption.NOFOLLOW_LINKS).use { inputStream ->
                inputStream.copyTo(archive.newDataOutputStream(), intervalMillis, listener)
            }
        } else {
            listener?.invoke(attributes.size())
        }
    }

    /** Writes an already-open archive entry without reopening the source archive. */
    @Throws(IOException::class)
    fun write(
        entry: ReadArchive.Entry,
        entryName: String,
        data: InputStream?,
        intervalMillis: Long,
        listener: ((Long) -> Unit)?
    ) {
        val normalizedName = ArchiveEntryValidator.sanitizeEntryName(entryName, entry.isDirectory)
            ?: throw IOException("Unsafe archive entry name")
        archive.Entry(
            normalizedName,
            entry.lastModifiedTime,
            entry.lastAccessTime,
            entry.creationTime,
            entry.type,
            entry.size.coerceAtLeast(0),
            entry.owner,
            entry.group,
            entry.mode,
            entry.symbolicLinkTarget
        ).use(archive::writeEntry)
        if (entry.type == PosixFileType.REGULAR_FILE) {
            requireNotNull(data) { "Regular archive entry requires a data stream" }
            data.copyTo(archive.newDataOutputStream(), intervalMillis, listener)
        } else {
            listener?.invoke(0)
        }
    }

    @Throws(IOException::class)
    fun writeDirectory(entryName: String) {
        val normalizedName = ArchiveEntryValidator.sanitizeEntryName(entryName, true)
            ?: return
        val directory = ReadArchive.Entry(
            normalizedName,
            false,
            null,
            null,
            null,
            PosixFileType.DIRECTORY,
            0,
            null,
            null,
            PosixFileMode.DIRECTORY_DEFAULT,
            null,
            null
        )
        write(directory, normalizedName, null, 0, null)
    }

    @Throws(IOException::class)
    override fun close() {
        archive.close()
    }
}
