// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.rclone

import android.os.Parcel
import com.wisso.wizefiles.provider.common.ByteString
import com.wisso.wizefiles.provider.common.ByteStringListPath
import com.wisso.wizefiles.provider.common.PollingWatchService
import com.wisso.wizefiles.provider.common.UriAuthority
import com.wisso.wizefiles.provider.common.toByteString
import com.wisso.wizefiles.util.readParcelable
import java.io.File
import java.io.IOException
import java.nio.file.FileSystem
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.ProviderMismatchException
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService

internal class RclonePath : ByteStringListPath<RclonePath> {
    private val rcloneFileSystem: RcloneFileSystem

    constructor(fileSystem: RcloneFileSystem, path: ByteString) :
        super(RcloneFileSystem.SEPARATOR, path) {
        rcloneFileSystem = fileSystem
    }

    private constructor(
        fileSystem: RcloneFileSystem,
        absolute: Boolean,
        segments: List<ByteString>
    ) : super(RcloneFileSystem.SEPARATOR, absolute, segments) {
        rcloneFileSystem = fileSystem
    }

    override fun isPathAbsolute(path: ByteString): Boolean =
        path.isNotEmpty() && path[0] == RcloneFileSystem.SEPARATOR

    override fun createPath(path: ByteString): RclonePath = RclonePath(rcloneFileSystem, path)

    override fun createPath(absolute: Boolean, segments: List<ByteString>): RclonePath =
        RclonePath(rcloneFileSystem, absolute, segments)

    override val uriScheme: String
        get() = RcloneFileSystemProvider.SCHEME

    override val uriAuthority: UriAuthority
        get() = UriAuthority(null, rcloneFileSystem.remoteName, null)

    override val uriQuery: ByteString?
        get() = null

    override val defaultDirectory: RclonePath
        get() = rcloneFileSystem.defaultDirectory

    override fun getFileSystem(): FileSystem = rcloneFileSystem

    override fun getRoot(): RclonePath? =
        if (isAbsolute) rcloneFileSystem.rootDirectory else null

    override fun toRealPath(vararg options: LinkOption): RclonePath = toAbsolutePath().normalize()

    override fun toFile(): File = throw UnsupportedOperationException()

    @Throws(IOException::class)
    override fun register(
        watcher: WatchService,
        events: Array<WatchEvent.Kind<*>>,
        vararg modifiers: WatchEvent.Modifier
    ): WatchKey {
        if (watcher !is PollingWatchService) {
            throw ProviderMismatchException(watcher.toString())
        }
        return watcher.register(this, events, *modifiers)
    }

    val remoteName: String
        get() = rcloneFileSystem.remoteName

    val remotePath: String
        get() = toAbsolutePath().normalize().toString().trimStart('/')

    private constructor(source: Parcel) : super(source) {
        rcloneFileSystem = source.readParcelable()!!
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)
        dest.writeParcelable(rcloneFileSystem, flags)
    }

    companion object {
        @JvmField
        val CREATOR = object : android.os.Parcelable.Creator<RclonePath> {
            override fun createFromParcel(source: Parcel): RclonePath = RclonePath(source)

            override fun newArray(size: Int): Array<RclonePath?> = arrayOfNulls(size)
        }
    }
}

val Path.isRclonePath: Boolean
    get() = this is RclonePath
