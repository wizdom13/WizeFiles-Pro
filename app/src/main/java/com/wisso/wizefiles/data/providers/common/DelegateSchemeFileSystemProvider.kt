// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import java.nio.channels.SeekableByteChannel
import java.nio.file.AccessMode
import java.nio.file.CopyOption
import java.nio.file.DirectoryStream
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileAttributeView
import java.nio.file.spi.FileSystemProvider
import java.net.URI

class DelegateSchemeFileSystemProvider(
    private val scheme: String,
    private val fileSystemProvider: FileSystemProvider
) : FileSystemProvider() {
    override fun getScheme(): String = scheme

    override fun newFileSystem(uri: URI, env: Map<String, *>): FileSystem =
        fileSystemProvider.newFileSystem(uri, env)

    override fun getFileSystem(uri: URI): FileSystem = fileSystemProvider.getFileSystem(uri)

    override fun getPath(uri: URI): Path = fileSystemProvider.getPath(uri)

    override fun newByteChannel(
        file: Path,
        options: Set<OpenOption>,
        vararg attributes: FileAttribute<*>
    ): SeekableByteChannel = throw NotImplementedError()

    override fun newDirectoryStream(
        directory: Path,
        filter: DirectoryStream.Filter<in Path>
    ): DirectoryStream<Path> = throw NotImplementedError()

    override fun createDirectory(directory: Path, vararg attributes: FileAttribute<*>) =
        throw NotImplementedError()

    override fun delete(path: Path) = throw NotImplementedError()

    override fun copy(source: Path, target: Path, vararg options: CopyOption) =
        throw NotImplementedError()

    override fun move(source: Path, target: Path, vararg options: CopyOption) =
        throw NotImplementedError()

    override fun isSameFile(path: Path, path2: Path): Boolean = throw NotImplementedError()

    override fun isHidden(path: Path): Boolean = throw NotImplementedError()

    override fun getFileStore(path: Path): FileStore = throw NotImplementedError()

    override fun checkAccess(path: Path, vararg modes: AccessMode) = throw NotImplementedError()

    override fun <V : FileAttributeView> getFileAttributeView(
        path: Path,
        type: Class<V>,
        vararg options: LinkOption
    ): V? = throw NotImplementedError()

    override fun <A : BasicFileAttributes> readAttributes(
        path: Path,
        type: Class<A>,
        vararg options: LinkOption
    ): A = throw NotImplementedError()

    override fun readAttributes(
        path: Path,
        attributes: String,
        vararg options: LinkOption
    ): Map<String, Any> = throw NotImplementedError()

    override fun setAttribute(
        path: Path,
        attribute: String,
        value: Any,
        vararg options: LinkOption
    ) = throw NotImplementedError()
}