// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.archive.archiver

import androidx.preference.PreferenceManager
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.app.application
import com.wisso.wizefiles.provider.common.ForwardingForceableSeekableByteChannel
import com.wisso.wizefiles.provider.common.ForwardingInputStream
import com.wisso.wizefiles.provider.common.ForwardingPlainSeekableByteChannel
import com.wisso.wizefiles.provider.common.ForceableChannel
import com.wisso.wizefiles.provider.common.PosixFileMode
import com.wisso.wizefiles.provider.common.PosixFileType
import com.wisso.wizefiles.provider.common.newByteChannel
import com.wisso.wizefiles.provider.common.newInputStream
import com.wisso.wizefiles.provider.root.isRunningAsRoot
import com.wisso.wizefiles.provider.root.rootContext
import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.util.valueCompat
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.nio.channels.SeekableByteChannel
import java.nio.charset.StandardCharsets
import java.nio.charset.Charset
import java.nio.file.Path

object ArchiveReader {
    /**
     * Iterates the archive once and keeps each entry data stream valid only for the callback.
     * The callback must consume regular-file data before returning.
     */
    @Throws(IOException::class)
    fun forEachEntry(
        file: Path,
        passwords: List<String>,
        action: (ReadArchive.Entry, InputStream?) -> Unit
    ) {
        val charset = archiveFileNameCharset
        val limits = ArchiveEntryValidator.LimitsState()
        val duplicates = mutableSetOf<String>()
        val (archive, closeable) = openArchive(file, passwords)
        closeable.use {
            while (true) {
                val entry = archive.readEntry(charset) ?: break
                enforceBombLimitsForEntry(entry, limits)
                if (entry.isEncrypted) throw IOException("Encrypted archives are read-only")
                if (entry.isSymbolicLink &&
                    !ArchiveEntryValidator.validateSymlinkTarget(entry.symbolicLinkTarget.orEmpty())) {
                    throw IOException("Unsafe archive symlink target")
                }
                val normalized = sanitizeArchiveEntryName(entry.name, entry.isDirectory) ?: continue
                if (!duplicates.add(normalized.lowercase())) {
                    throw IOException("Duplicate archive entry path")
                }
                val data = if (entry.type == PosixFileType.REGULAR_FILE) {
                    archive.newDataInputStream()
                } else {
                    null
                }
                action(entry, data)
                if (data != null) {
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (data.read(buffer) != -1) Unit
                }
            }
        }
    }

    @Throws(IOException::class)
    fun entryMetadata(file: Path, passwords: List<String> = emptyList()): List<ReadArchive.Entry> {
        val result = mutableListOf<ReadArchive.Entry>()
        forEachEntry(file, passwords) { entry, _ -> result += entry }
        return result
    }

    @Throws(IOException::class)
    fun readEntries(
        file: Path,
        passwords: List<String>,
        rootPath: Path
    ): Pair<Map<Path, ReadArchive.Entry>, Map<Path, List<Path>>> {
        val entries = mutableMapOf<Path, ReadArchive.Entry>()
        val duplicateGuard = mutableSetOf<String>()
        val limits = ArchiveEntryValidator.LimitsState()
        val rawEntries = readEntries(file, passwords)
        for (entry in rawEntries) {
            enforceBombLimitsForEntry(entry, limits)
            if (entry.isSymbolicLink && !ArchiveEntryValidator.validateSymlinkTarget(entry.symbolicLinkTarget.orEmpty())) {
                throw IOException("Unsafe archive symlink target")
            }
            val path = sanitizeArchiveEntryPath(entry, rootPath) ?: continue
            val duplicateKey = ArchiveEntryValidator.duplicateKey(path)
            if (!duplicateGuard.add(duplicateKey)) {
                throw IOException("Duplicate archive entry path")
            }
            entries[path] = entry
        }
        entries.getOrPut(rootPath) { createDirectoryEntry("") }
        val tree = mutableMapOf<Path, MutableList<Path>>()
        tree[rootPath] = mutableListOf()
        val paths = entries.keys.toList()
        for (path in paths) {
            var path = path
            while (true) {
                val parentPath = path.parent ?: break
                val entry = entries[path]!!
                if (entry.isDirectory) {
                    tree.getOrPut(path) { mutableListOf() }
                }
                tree.getOrPut(parentPath) { mutableListOf() }.add(path)
                if (entries.containsKey(parentPath)) {
                    break
                }
                entries[parentPath] = createDirectoryEntry(parentPath.toString())
                path = parentPath
            }
        }
        return entries to tree
    }

    internal fun sanitizeArchiveEntryPath(entry: ReadArchive.Entry, rootPath: Path): Path? {
        val sanitizedEntryName = sanitizeArchiveEntryName(entry.name, entry.isDirectory) ?: return null
        val path = rootPath.resolve(sanitizedEntryName).normalize()
        if (!path.isAbsolute) {
            throw AssertionError("Path must be absolute: $path")
        }
        if (!path.startsWith(rootPath)) {
            throw IOException("Unsafe archive entry path")
        }
        return path
    }

    internal fun sanitizeArchiveEntryName(entryName: String, isDirectory: Boolean): String? =
        ArchiveEntryValidator.sanitizeEntryName(entryName, isDirectory)

    internal fun enforceBombLimitsForEntry(
        entry: ReadArchive.Entry,
        limits: ArchiveEntryValidator.LimitsState
    ) {
        // Expansion ratio is enforced only when compressedSize is known for this entry.
        ArchiveEntryValidator.checkArchiveBombLimits(limits, entry.size, entry.compressedSize)
    }

    private fun createDirectoryEntry(name: String): ReadArchive.Entry {
        require(!name.endsWith("/")) { "name $name should not end with a slash" }
        return ReadArchive.Entry(
            name, false, null, null, null, PosixFileType.DIRECTORY, 0, null, null,
            PosixFileMode.DIRECTORY_DEFAULT, null, null
        )
    }

    @Throws(IOException::class)
    private fun readEntries(file: Path, passwords: List<String>): List<ReadArchive.Entry> =
        try {
            readEntriesWithLibarchive(file, passwords)
        } catch (primaryFailure: IOException) {
            if (primaryFailure is InterruptedIOException || Thread.currentThread().isInterrupted) {
                throw primaryFailure
            }
            try {
                SevenZipNative.readEntries(file, passwords)
            } catch (fallbackFailure: IOException) {
                fallbackFailure.addSuppressed(primaryFailure)
                throw fallbackFailure
            }
        }

    @Throws(IOException::class)
    private fun readEntriesWithLibarchive(
        file: Path,
        passwords: List<String>
    ): List<ReadArchive.Entry> {
        val charset = archiveFileNameCharset
        val (archive, closeable) = openArchive(file, passwords)
        return closeable.use {
            buildList {
                while (true) {
                    this += archive.readEntry(charset) ?: break
                }
            }
        }
    }

    @Throws(IOException::class)
    fun newInputStream(file: Path, passwords: List<String>, entry: ReadArchive.Entry): InputStream? {
        if (entry.backend == ArchiveReadBackend.SEVEN_ZIP) {
            return SevenZipNative.newInputStream(file, passwords, entry, application.cacheDir)
        }
        val charset = archiveFileNameCharset
        val (archive, closeable) = openArchive(file, passwords)
        var successful = false
        return try {
            while (true) {
                val currentEntry = archive.readEntry(charset) ?: break
                if (currentEntry.name != entry.name) {
                    continue
                }
                successful = true
                break
            }
            if (successful) {
                CloseableInputStream(archive.newDataInputStream(), closeable)
            } else {
                null
            }
        } finally {
            if (!successful) {
                closeable.close()
            }
        }
    }

    @Throws(IOException::class)
    private fun openArchive(
        file: Path,
        passwords: List<String>
    ): Pair<ReadArchive, ArchiveCloseable> {
        val channel = try {
            CacheSizeSeekableByteChannel(file.newByteChannel()).let { candidate ->
                if (isUsableSeekableChannel(candidate)) {
                    candidate
                } else {
                    candidate.close()
                    null
                }
            }
        } catch (e: Exception) {
            com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
            null
        }
        if (channel != null) {
            var successful = false
            try {
                val archive = ReadArchive(channel, passwords)
                successful = true
                return archive to ArchiveCloseable(archive, channel)
            } finally {
                if (!successful) {
                    channel.close()
                }
            }
        }
        val inputStream = file.newInputStream()
        var successful = false
        try {
            val archive = ReadArchive(inputStream, passwords)
            successful = true
            return archive to ArchiveCloseable(archive, inputStream)
        } finally {
            if (!successful) {
                inputStream.close()
            }
        }
    }

    internal fun isUsableSeekableChannel(channel: SeekableByteChannel): Boolean =
        try {
            val position = channel.position()
            channel.position(position)
            channel.size()
            true
        } catch (e: Exception) {
            com.wisso.wizefiles.util.AppLog.e(
                "ArchiveReader",
                "Archive source does not support seeking; falling back to streaming",
                e
            )
            false
        }

    // size() may be called repeatedly for ZIP and 7Z, so make it cached to improve performance.
    private fun CacheSizeSeekableByteChannel(channel: SeekableByteChannel): SeekableByteChannel =
        if (channel is ForceableChannel) {
            CacheSizeForceableSeekableByteChannel(channel)
        } else {
            CacheSizeNonForceableSeekableByteChannel(channel)
        }

    private class CacheSizeNonForceableSeekableByteChannel(
        channel: SeekableByteChannel
    ) : ForwardingPlainSeekableByteChannel(channel) {
        private val size: Long by lazy { super.size() }

        override fun size(): Long = size
    }

    private class CacheSizeForceableSeekableByteChannel(
        channel: SeekableByteChannel
    ) : ForwardingForceableSeekableByteChannel(channel) {
        private val size: Long by lazy { super.size() }

        override fun size(): Long = size
    }

    private val archiveFileNameCharset: Charset
        get() =
            if (isRunningAsRoot) {
                try {
                    val sharedPreferences =
                        PreferenceManager.getDefaultSharedPreferences(rootContext)
                    val key = rootContext.getString(R.string.pref_key_archive_file_name_encoding)
                    val defaultValue = rootContext.getString(
                        R.string.pref_default_value_archive_file_name_encoding
                    )
                    Charset.forName(sharedPreferences.getString(key, defaultValue)!!)
                } catch (e: Exception) {
                    com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
                    StandardCharsets.UTF_8
                }
            } else {
                Charset.forName(Settings.ARCHIVE_FILE_NAME_ENCODING.valueCompat)
            }

    private class ArchiveCloseable(
        private val archive: ReadArchive,
        private val closeable: Closeable
    ) : Closeable {
        override fun close() {
            @Suppress("ConvertTryFinallyToUseCall")
            try {
                archive.close()
            } finally {
                closeable.close()
            }
        }
    }

    private class CloseableInputStream(
        inputStream: InputStream,
        private val closeable: Closeable
    ) : ForwardingInputStream(inputStream) {
        @Throws(IOException::class)
        override fun close() {
            super.close()

            closeable.close()
        }
    }
}
