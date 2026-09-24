// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.archive

import android.os.Parcel
import android.os.Parcelable
import java.nio.file.ClosedFileSystemException
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import java.nio.file.NotLinkException
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.WatchService
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.file.spi.FileSystemProvider
import com.wisso.wizefiles.provider.archive.archiver.ArchiveReader
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.provider.archive.legacy.requireLegacyArchivePath
import com.wisso.wizefiles.provider.archive.legacy.toArchiveAppPath
import com.wisso.wizefiles.provider.archive.archiver.ReadArchive
import com.wisso.wizefiles.core.app.application
import com.wisso.wizefiles.provider.common.ByteString
import com.wisso.wizefiles.provider.common.ByteStringBuilder
import com.wisso.wizefiles.provider.common.ByteStringListPathCreator
import com.wisso.wizefiles.util.readParcelable
import com.wisso.wizefiles.provider.common.IsDirectoryException
import com.wisso.wizefiles.provider.common.newInputStream
import com.wisso.wizefiles.provider.common.toByteString
import com.wisso.wizefiles.provider.content.isContentPath
import com.wisso.libarchive.ArchiveException
import java.io.File
import java.io.IOException
import java.io.InputStream

internal class ArchiveFileSystem(
    private val provider: ArchiveFileSystemProvider,
    val archiveFile: AppPath
) : FileSystem(), ByteStringListPathCreator, Parcelable {
    constructor(provider: ArchiveFileSystemProvider, archiveFile: Path) :
        this(provider, archiveFile.toArchiveAppPath())
    val rootDirectory = ArchivePath(this, SEPARATOR_BYTE_STRING)

    init {
        if (!rootDirectory.isAbsolute) {
            throw AssertionError("Root directory $rootDirectory must be absolute")
        }
        if (rootDirectory.nameCount != 0) {
            throw AssertionError("Root directory $rootDirectory must contain no names")
        }
    }

    val defaultDirectory: ArchivePath
        get() = rootDirectory

    private val lock = Any()

    private var isOpen = true

    private var passwords = listOf<String>()

    private var isRefreshNeeded = true

    private var entries: Map<Path, ReadArchive.Entry>? = null

    private var tree: Map<Path, List<Path>>? = null

    private var stagedArchiveFile: Path? = null

    @Throws(IOException::class)
    fun getEntry(path: Path): ReadArchive.Entry =
        synchronized(lock) {
            ensureEntriesLocked(path)
            getEntryLocked(path)
        }

    @Throws(IOException::class)
    private fun getEntryLocked(path: Path): ReadArchive.Entry =
        synchronized(lock) {
            entries!![path] ?: throw NoSuchFileException(path.toString())
        }

    @Throws(IOException::class)
    fun newInputStream(file: Path): InputStream =
        synchronized(lock) {
            ensureEntriesLocked(file)
            val entry = getEntryLocked(file)
            if (entry.isDirectory) {
                throw IsDirectoryException(file.toString())
            }
            val inputStream = try {
                newEntryInputStreamWithContentFallbackLocked(entry)
            } catch (e: ArchiveException) {
                throw e.toFileSystemOrInterruptedIOException(file)
            } ?: throw NoSuchFileException(file.toString())
            ArchiveExceptionInputStream(inputStream, file)
        }

    @Throws(IOException::class)
    fun getDirectoryChildren(directory: Path): List<Path> =
        synchronized(lock) {
            ensureEntriesLocked(directory)
            val entry = getEntryLocked(directory)
            if (!entry.isDirectory) {
                throw NotDirectoryException(directory.toString())
            }
            tree!![directory]!!
        }

    @Throws(IOException::class)
    fun readSymbolicLink(link: Path): String =
        synchronized(lock) {
            ensureEntriesLocked(link)
            val entry = getEntryLocked(link)
            if (!entry.isSymbolicLink) {
                throw NotLinkException(link.toString())
            }
            entry.symbolicLinkTarget.orEmpty()
        }

    fun addPassword(password: String) {
        synchronized(lock) {
            if (!isOpen) {
                throw ClosedFileSystemException()
            }
            val trimmed = password.trim()
            if (trimmed.isEmpty()) {
                return
            }
            passwords = (passwords + trimmed).distinct().takeLast(MAX_PASSWORD_ATTEMPTS)
        }
    }

    fun refresh() {
        synchronized(lock) {
            if (!isOpen) {
                throw ClosedFileSystemException()
            }
            clearStagedArchiveFileLocked()
            isRefreshNeeded = true
        }
    }

    @Throws(IOException::class)
    private fun ensureEntriesLocked(file: Path) {
        if (!isOpen) {
            throw ClosedFileSystemException()
        }
        if (isRefreshNeeded) {
            val entriesAndTree = try {
                readEntriesWithContentFallbackLocked()
            } catch (e: ArchiveException) {
                throw e.toFileSystemOrInterruptedIOException(file)
            }
            entries = entriesAndTree.first
            tree = entriesAndTree.second
            isRefreshNeeded = false
        }
    }

    @Throws(IOException::class)
    private fun newEntryInputStreamWithContentFallbackLocked(
        entry: ReadArchive.Entry
    ): InputStream? {
        val sourceFile = readerArchiveFileLocked()
        return try {
            ArchiveReader.newInputStream(sourceFile, passwords, entry)
        } catch (originalFailure: IOException) {
            if (stagedArchiveFile != null || !sourceFile.isContentPath) {
                throw originalFailure
            }
            val stagedFile = try {
                stageArchiveFileLocked(sourceFile)
            } catch (stagingFailure: IOException) {
                stagingFailure.addSuppressed(originalFailure)
                throw stagingFailure
            }
            try {
                ArchiveReader.newInputStream(stagedFile, passwords, entry)
            } catch (retryFailure: IOException) {
                retryFailure.addSuppressed(originalFailure)
                throw retryFailure
            }
        }
    }

    @Throws(IOException::class)
    private fun readEntriesWithContentFallbackLocked():
        Pair<Map<Path, ReadArchive.Entry>, Map<Path, List<Path>>> {
        stagedArchiveFile?.let { stagedFile ->
            return ArchiveReader.readEntries(stagedFile, passwords, rootDirectory)
        }
        val sourceFile = archiveFile.requireLegacyArchivePath()
        // Content providers can expose a descriptor that appears seekable while still reporting
        // incomplete ZIP metadata on the first pass. Materialize the external archive once before
        // reading its directory so entry sizes and other central-directory metadata are correct
        // immediately, not only after a manual refresh.
        if (sourceFile.isContentPath) {
            val stagedFile = stageArchiveFileLocked(sourceFile)
            return ArchiveReader.readEntries(stagedFile, passwords, rootDirectory)
        }
        return ArchiveReader.readEntries(sourceFile, passwords, rootDirectory)
    }

    private fun readerArchiveFileLocked(): Path =
        stagedArchiveFile ?: archiveFile.requireLegacyArchivePath()

    @Throws(IOException::class)
    private fun stageArchiveFileLocked(sourceFile: Path): Path {
        val temporaryFile = File.createTempFile(
            "external-archive-",
            ".tmp",
            application.cacheDir
        )
        try {
            sourceFile.newInputStream().use { inputStream ->
                temporaryFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } catch (failure: Exception) {
            temporaryFile.delete()
            throw failure
        }
        return temporaryFile.toPath().also { stagedArchiveFile = it }
    }

    private fun clearStagedArchiveFileLocked() {
        stagedArchiveFile?.toFile()?.delete()
        stagedArchiveFile = null
    }

    override fun provider(): FileSystemProvider = provider

    override fun close() {
        synchronized(lock) {
            if (!isOpen) {
                return
            }
            provider.removeFileSystem(this)
            clearStagedArchiveFileLocked()
            isRefreshNeeded = false
            entries = null
            tree = null
            passwords = emptyList()
            isOpen = false
        }
    }

    override fun isOpen(): Boolean = synchronized(lock) { isOpen }

    override fun isReadOnly(): Boolean = true

    override fun getSeparator(): String = SEPARATOR_STRING

    override fun getRootDirectories(): Iterable<Path> = listOf(rootDirectory)

    override fun getFileStores(): Iterable<FileStore> {
        // TODO
        throw UnsupportedOperationException()
    }

    override fun supportedFileAttributeViews(): Set<String> =
        ArchiveFileAttributeView.SUPPORTED_NAMES

    override fun getPath(first: String, vararg more: String): ArchivePath {
        val path = ByteStringBuilder(first.toByteString())
            .apply { more.forEach { append(SEPARATOR).append(it.toByteString()) } }
            .toByteString()
        return ArchivePath(this, path)
    }

    override fun getPath(first: ByteString, vararg more: ByteString): ArchivePath {
        val path = ByteStringBuilder(first)
            .apply { more.forEach { append(SEPARATOR).append(it) } }
            .toByteString()
        return ArchivePath(this, path)
    }

    override fun getPathMatcher(syntaxAndPattern: String): PathMatcher {
        throw UnsupportedOperationException()
    }

    override fun getUserPrincipalLookupService(): UserPrincipalLookupService {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun newWatchService(): WatchService {
        // TODO
        throw UnsupportedOperationException()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }
        other as ArchiveFileSystem
        return archiveFile == other.archiveFile
    }

    override fun hashCode(): Int = archiveFile.hashCode()

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(archiveFile, flags)
    }

    companion object {
        private const val MAX_PASSWORD_ATTEMPTS = 3
        const val SEPARATOR = '/'.code.toByte()
        private val SEPARATOR_BYTE_STRING = SEPARATOR.toByteString()
        private const val SEPARATOR_STRING = SEPARATOR.toInt().toChar().toString()

        @JvmField
        val CREATOR = object : Parcelable.Creator<ArchiveFileSystem> {
            override fun createFromParcel(source: Parcel): ArchiveFileSystem {
                val archiveFile = source.readParcelable<Parcelable>()
                    as AppPath
                return ArchiveFileSystemProvider.getOrNewFileSystem(archiveFile)
            }

            override fun newArray(size: Int): Array<ArchiveFileSystem?> = arrayOfNulls(size)
        }
    }
}
