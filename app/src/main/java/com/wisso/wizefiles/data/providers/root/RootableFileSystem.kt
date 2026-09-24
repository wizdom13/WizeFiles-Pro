package com.wisso.wizefiles.provider.root

import android.os.Parcelable
import java.io.IOException
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.WatchService
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.file.spi.FileSystemProvider

abstract class RootableFileSystem(
    createLocal: (FileSystem) -> FileSystem,
    createRoot: (FileSystem) -> RootFileSystem
) : FileSystem(), Parcelable {
    protected open val localFileSystem: FileSystem = createLocal(this)
    protected open val rootFileSystem: RootFileSystem = createRoot(this)

    final override fun provider(): FileSystemProvider = localFileSystem.provider()
    final override fun isOpen(): Boolean = localFileSystem.isOpen
    final override fun isReadOnly(): Boolean = localFileSystem.isReadOnly
    final override fun getSeparator(): String = localFileSystem.separator
    final override fun getRootDirectories(): Iterable<Path> = localFileSystem.rootDirectories
    final override fun getFileStores(): Iterable<FileStore> = localFileSystem.fileStores
    final override fun supportedFileAttributeViews(): Set<String> =
        localFileSystem.supportedFileAttributeViews()

    final override fun getPath(first: String, vararg more: String): Path =
        localFileSystem.getPath(first, *more)

    final override fun getPathMatcher(syntaxAndPattern: String): PathMatcher =
        localFileSystem.getPathMatcher(syntaxAndPattern)

    final override fun getUserPrincipalLookupService(): UserPrincipalLookupService =
        localFileSystem.userPrincipalLookupService

    final override fun newWatchService(): WatchService = localFileSystem.newWatchService()

    @Throws(IOException::class)
    override fun close() {
        if (!localFileSystem.isOpen) return
        var failure: Throwable? = null
        try {
            localFileSystem.close()
        } catch (caught: Throwable) {
            failure = caught
        }
        try {
            rootFileSystem.close()
        } catch (caught: Throwable) {
            if (failure == null) failure = caught else failure?.addSuppressed(caught)
        }
        when (val caught = failure) {
            null -> Unit
            is IOException -> throw caught
            is RuntimeException -> throw caught
            is Error -> throw caught
            else -> throw IOException("Unable to close rootable file system", caught)
        }
    }

    final override fun equals(other: Any?): Boolean =
        other === this || other?.javaClass === javaClass &&
            (other as RootableFileSystem).localFileSystem == localFileSystem

    final override fun hashCode(): Int = localFileSystem.hashCode()
}
