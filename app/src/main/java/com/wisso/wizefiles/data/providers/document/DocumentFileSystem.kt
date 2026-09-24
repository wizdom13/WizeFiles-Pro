// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.document

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.WatchService
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.file.spi.FileSystemProvider
import com.wisso.wizefiles.provider.common.ByteString
import com.wisso.wizefiles.provider.common.ByteStringBuilder
import com.wisso.wizefiles.provider.common.ByteStringListPathCreator
import com.wisso.wizefiles.provider.common.toByteString
import com.wisso.wizefiles.util.StableUriParceler
import java.io.IOException

internal class DocumentFileSystem(
    private val provider: DocumentFileSystemProvider,
    val treeUri: Uri
) : FileSystem(), ByteStringListPathCreator, Parcelable {
    val rootDirectory = DocumentPath(this, SEPARATOR_BYTE_STRING)

    init {
        if (!rootDirectory.isAbsolute) {
            throw AssertionError("Root directory must be absolute")
        }
        if (rootDirectory.nameCount != 0) {
            throw AssertionError("Root directory must contain no names")
        }
    }

    private val lock = Any()

    private var isOpen = true

    val defaultDirectory: DocumentPath
        get() = rootDirectory

    override fun provider(): FileSystemProvider = provider

    override fun close() {
        synchronized(lock) {
            if (!isOpen) {
                return
            }
            provider.removeFileSystem(this)
            isOpen = false
        }
    }

    override fun isOpen(): Boolean = synchronized(lock) { isOpen }

    override fun isReadOnly(): Boolean = false

    override fun getSeparator(): String = SEPARATOR_STRING

    override fun getRootDirectories(): Iterable<Path> = listOf(rootDirectory)

    override fun getFileStores(): Iterable<FileStore> {
        // TODO
        throw UnsupportedOperationException()
    }

    override fun supportedFileAttributeViews(): Set<String> =
        DocumentFileAttributeView.SUPPORTED_NAMES

    override fun getPath(first: String, vararg more: String): DocumentPath {
        val path = ByteStringBuilder(first.toByteString())
            .apply { more.forEach { append(SEPARATOR).append(it.toByteString()) } }
            .toByteString()
        return DocumentPath(this, path)
    }

    override fun getPath(first: ByteString, vararg more: ByteString): DocumentPath {
        val path = ByteStringBuilder(first)
            .apply { more.forEach { append(SEPARATOR).append(it) } }
            .toByteString()
        return DocumentPath(this, path)
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
        other as DocumentFileSystem
        return treeUri == other.treeUri
    }

    override fun hashCode(): Int = treeUri.hashCode()

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        //dest.writeParcelable(treeUri, flags)
        with(StableUriParceler) { treeUri.write(dest, flags) }
    }

    companion object {
        const val SEPARATOR = '/'.code.toByte()
        private val SEPARATOR_BYTE_STRING = SEPARATOR.toByteString()
        private const val SEPARATOR_STRING = SEPARATOR.toInt().toChar().toString()

        @JvmField
        val CREATOR = object : Parcelable.Creator<DocumentFileSystem> {
            override fun createFromParcel(source: Parcel): DocumentFileSystem {
                //val treeUri = source.readParcelable<Uri>()!!
                val treeUri = StableUriParceler.create(source)!!
                return DocumentFileSystemProvider.getOrNewFileSystem(treeUri)
            }

            override fun newArray(size: Int): Array<DocumentFileSystem?> = arrayOfNulls(size)
        }
    }
}
