package com.wisso.wizefiles.provider.rclone

import android.os.Parcel
import android.os.Parcelable
import com.wisso.wizefiles.provider.common.ByteString
import com.wisso.wizefiles.provider.common.ByteStringBuilder
import com.wisso.wizefiles.provider.common.ByteStringListPathCreator
import com.wisso.wizefiles.provider.common.PollingWatchService
import com.wisso.wizefiles.provider.common.toByteString
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.WatchService
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.file.spi.FileSystemProvider

internal class RcloneFileSystem(
    private val fileSystemProvider: RcloneFileSystemProvider,
    val remoteName: String
) : FileSystem(), ByteStringListPathCreator, Parcelable {
    val rootDirectory = RclonePath(this, SEPARATOR_BYTE_STRING)

    private val lock = Any()
    private var open = true

    val defaultDirectory: RclonePath
        get() = rootDirectory

    override fun provider(): FileSystemProvider = fileSystemProvider

    override fun close() {
        synchronized(lock) {
            if (!open) return
            fileSystemProvider.removeFileSystem(this)
            open = false
        }
    }

    override fun isOpen(): Boolean = synchronized(lock) { open }

    override fun isReadOnly(): Boolean = false

    override fun getSeparator(): String = "/"

    override fun getRootDirectories(): Iterable<Path> = listOf(rootDirectory)

    override fun getFileStores(): Iterable<FileStore> = emptyList()

    override fun supportedFileAttributeViews(): Set<String> = setOf("basic")

    override fun getPath(first: String, vararg more: String): RclonePath =
        RclonePath(
            this,
            ByteStringBuilder(first.toByteString())
                .apply { more.forEach { append(SEPARATOR).append(it.toByteString()) } }
                .toByteString()
        )

    override fun getPath(first: ByteString, vararg more: ByteString): RclonePath =
        RclonePath(
            this,
            ByteStringBuilder(first)
                .apply { more.forEach { append(SEPARATOR).append(it) } }
                .toByteString()
        )

    override fun getPathMatcher(syntaxAndPattern: String): PathMatcher =
        throw UnsupportedOperationException()

    override fun getUserPrincipalLookupService(): UserPrincipalLookupService =
        throw UnsupportedOperationException()

    override fun newWatchService(): WatchService = PollingWatchService()

    override fun equals(other: Any?): Boolean =
        other is RcloneFileSystem && remoteName == other.remoteName

    override fun hashCode(): Int = remoteName.hashCode()

    override fun describeContents(): Int = 0

    override fun writeToParcel(destination: Parcel, flags: Int) {
        destination.writeString(remoteName)
    }

    companion object {
        const val SEPARATOR = '/'.code.toByte()
        private val SEPARATOR_BYTE_STRING = SEPARATOR.toByteString()

        @JvmField
        val CREATOR = object : Parcelable.Creator<RcloneFileSystem> {
            override fun createFromParcel(source: Parcel): RcloneFileSystem =
                RcloneFileSystemProvider.getOrNewFileSystem(source.readString()!!)

            override fun newArray(size: Int): Array<RcloneFileSystem?> = arrayOfNulls(size)
        }
    }
}
