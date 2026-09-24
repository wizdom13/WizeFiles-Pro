// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import android.os.Parcelable
import java.io.File
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import kotlinx.parcelize.Parcelize

@Parcelize
class ByteStringPath(
    private val encodedPath: ByteString
) : Path, Parcelable {

    fun toByteString(): ByteString = encodedPath

    override fun toString(): String = encodedPath.toString()

    override fun getFileSystem(): FileSystem = unsupported()
    override fun isAbsolute(): Boolean = unsupported()
    override fun getRoot(): Path = unsupported()
    override fun getFileName(): Path = unsupported()
    override fun getParent(): Path = unsupported()
    override fun getNameCount(): Int = unsupported()
    override fun getName(index: Int): Path = unsupported()
    override fun subpath(beginIndex: Int, endIndex: Int): Path = unsupported()
    override fun startsWith(other: Path): Boolean = unsupported()
    override fun startsWith(other: String): Boolean = unsupported()
    override fun endsWith(other: Path): Boolean = unsupported()
    override fun endsWith(other: String): Boolean = unsupported()
    override fun normalize(): Path = unsupported()
    override fun resolve(other: Path): Path = unsupported()
    override fun resolve(other: String): Path = unsupported()
    override fun resolveSibling(other: Path): Path = unsupported()
    override fun resolveSibling(other: String): Path = unsupported()
    override fun relativize(other: Path): Path = unsupported()
    override fun toUri(): URI = unsupported()
    override fun toAbsolutePath(): Path = unsupported()
    override fun toRealPath(vararg options: LinkOption): Path = unsupported()
    override fun toFile(): File = unsupported()

    override fun register(
        watcher: WatchService,
        events: Array<WatchEvent.Kind<*>>,
        vararg modifiers: WatchEvent.Modifier
    ): WatchKey = unsupported()

    override fun register(
        watcher: WatchService,
        vararg events: WatchEvent.Kind<*>
    ): WatchKey = unsupported()

    override fun iterator(): MutableIterator<Path> = unsupported()
    override fun compareTo(other: Path): Int = unsupported()

    private fun unsupported(): Nothing =
        throw UnsupportedOperationException("ByteStringPath is a transport value, not a navigable path")
}
