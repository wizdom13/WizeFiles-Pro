// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.smb

import android.os.Parcel
import android.os.Parcelable
import java.nio.file.FileSystem
import java.nio.file.LinkOption
import java.nio.file.NotDirectoryException
import java.nio.file.Path
import java.nio.file.ProviderMismatchException
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import com.wisso.wizefiles.provider.common.ByteString
import com.wisso.wizefiles.provider.common.ByteStringListPath
import com.wisso.wizefiles.provider.common.UriAuthority
import com.wisso.wizefiles.provider.smb.client.Authority
import com.wisso.wizefiles.provider.smb.client.SmbClient
import com.wisso.wizefiles.util.readParcelable
import java.io.File
import java.io.IOException

internal class SmbPath : ByteStringListPath<SmbPath>, SmbClient.Path {
    private val fileSystem: SmbFileSystem

    constructor(
        fileSystem: SmbFileSystem,
        path: ByteString
    ) : super(SmbFileSystem.SEPARATOR, path) {
        this.fileSystem = fileSystem
    }

    private constructor(
        fileSystem: SmbFileSystem,
        absolute: Boolean,
        segments: List<ByteString>
    ) : super(SmbFileSystem.SEPARATOR, absolute, segments) {
        this.fileSystem = fileSystem
    }

    override fun isPathAbsolute(path: ByteString): Boolean =
        path.isNotEmpty() && path[0] == SmbFileSystem.SEPARATOR

    override fun createPath(path: ByteString): SmbPath = SmbPath(fileSystem, path)

    override fun createPath(absolute: Boolean, segments: List<ByteString>): SmbPath =
        SmbPath(fileSystem, absolute, segments)

    override val uriAuthority: UriAuthority
        get() = fileSystem.authority.toUriAuthority()

    override val defaultDirectory: SmbPath
        get() = fileSystem.defaultDirectory

    override fun getFileSystem(): FileSystem = fileSystem

    override fun getRoot(): SmbPath? = if (isAbsolute) fileSystem.rootDirectory else null

    @Throws(IOException::class)
    override fun toRealPath(vararg options: LinkOption): SmbPath {
        throw UnsupportedOperationException()
    }

    override fun toFile(): File {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun register(
        watcher: WatchService,
        events: Array<WatchEvent.Kind<*>>,
        vararg modifiers: WatchEvent.Modifier
    ): WatchKey {
        if (watcher !is SmbWatchService) {
            throw ProviderMismatchException(watcher.toString())
        }
        if (sharePath == null) {
            throw UnsupportedOperationException("Cannot watch SMB root path without a share: $this")
        }
        return try {
            watcher.register(this, events, *modifiers)
        } catch (e: NotDirectoryException) {
            throw UnsupportedOperationException("Cannot watch SMB non-directory path: $this", e)
        }
    }

    override val authority: Authority
        get() = fileSystem.authority

    override val sharePath: SmbClient.Path.SharePath? by lazy {
        check(isAbsolute)
        if (nameCount > 0) {
            SmbClient.Path.SharePath(
                getNameByteString(0).toString(),
                nameByteStrings.asSequence().drop(1).joinToString("\\")
            )
        } else {
            null
        }
    }

    fun toWindowsPath(): String =
        if (isAbsolute) {
            // Port cannot be specified in a Windows UNC path for SMB, or otherwise it is resolved
            // as a remote path.
            check(authority.port == Authority.DEFAULT_PORT) {
                "Path is absolute but uses port ${authority.port} instead of the default port ${
                Authority.DEFAULT_PORT}"
            }
            StringBuilder()
                .append("\\\\")
                .append(authority.host)
                .append("\\")
                .apply {
                    val share = sharePath
                    if (share != null) {
                        append(share.name)
                        append("\\")
                        append(share.path)
                    }
                }
                .toString()
        } else {
            nameByteStrings.joinToString("\\")
        }

    private constructor(source: Parcel) : super(source) {
        fileSystem = source.readParcelable()!!
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)

        dest.writeParcelable(fileSystem, flags)
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<SmbPath> {
            override fun createFromParcel(source: Parcel): SmbPath = SmbPath(source)

            override fun newArray(size: Int): Array<SmbPath?> = arrayOfNulls(size)
        }
    }
}

val Path.isSmbPath: Boolean
    get() = this is SmbPath
