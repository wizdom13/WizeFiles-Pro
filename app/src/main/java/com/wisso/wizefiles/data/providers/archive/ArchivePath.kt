package com.wisso.wizefiles.provider.archive

import android.os.Parcel
import android.os.Parcelable
import com.wisso.wizefiles.provider.common.ByteString
import com.wisso.wizefiles.provider.common.ByteStringListPath
import com.wisso.wizefiles.provider.common.toByteString
import com.wisso.wizefiles.provider.root.RootablePath
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.LocalAppPath
import com.wisso.wizefiles.util.readParcelable
import java.io.File
import java.io.IOException
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.WatchKey
import java.nio.file.WatchService

internal class ArchivePath : ByteStringListPath<ArchivePath>, RootablePath {
    private val fileSystem: ArchiveFileSystem

    constructor(fileSystem: ArchiveFileSystem, path: ByteString) : super(
        ArchiveFileSystem.SEPARATOR, path
    ) {
        this.fileSystem = fileSystem
    }

    private constructor(
        fileSystem: ArchiveFileSystem,
        absolute: Boolean,
        segments: List<ByteString>
    ) : super(ArchiveFileSystem.SEPARATOR, absolute, segments) {
        this.fileSystem = fileSystem
    }

    override fun isPathAbsolute(path: ByteString): Boolean =
        !path.isEmpty() && path[0] == ArchiveFileSystem.SEPARATOR

    override fun createPath(path: ByteString): ArchivePath = ArchivePath(fileSystem, path)

    override fun createPath(absolute: Boolean, segments: List<ByteString>): ArchivePath =
        ArchivePath(fileSystem, absolute, segments)

    override val uriPath: ByteString
        // Prepend a slash character to make it a valid URI path, since we always have an (empty)
        // authority.
        get() = ("/" + fileSystem.archiveFile.toArchiveUriString()).toByteString()

    override val uriQuery: ByteString?
        get() = super.uriPath

    override val defaultDirectory: ArchivePath
        get() = fileSystem.defaultDirectory

    override fun getFileSystem(): ArchiveFileSystem = fileSystem

    override fun getRoot(): ArchivePath? = if (isAbsolute) fileSystem.rootDirectory else null

    @Throws(IOException::class)
    override fun toRealPath(vararg options: LinkOption): ArchivePath {
        throw UnsupportedOperationException()
    }

    override fun toFile(): File {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun register(
        watcher: WatchService,
        vararg events: java.nio.file.WatchEvent.Kind<*>
    ): WatchKey {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun register(
        watcher: WatchService,
        events: Array<java.nio.file.WatchEvent.Kind<*>>,
        vararg modifiers: java.nio.file.WatchEvent.Modifier
    ): WatchKey {
        throw UnsupportedOperationException()
    }

    override fun isRootRequired(isAttributeAccess: Boolean): Boolean {
        val archiveFile = fileSystem.archiveFile
        return if (archiveFile is RootablePath) {
            archiveFile.isRootRequired(isAttributeAccess)
        } else {
            false
        }
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
        val CREATOR = object : Parcelable.Creator<ArchivePath> {
            override fun createFromParcel(source: Parcel): ArchivePath = ArchivePath(source)

            override fun newArray(size: Int): Array<ArchivePath?> = arrayOfNulls(size)
        }
    }
}

private fun AppPath.toArchiveUriString(): String = when (this) {
    is LocalAppPath -> file.toURI().toString()
    else -> rawPath
}

val Path.isArchivePath: Boolean
    get() = this is ArchivePath
