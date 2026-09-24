package com.wisso.wizefiles.provider.archive

import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.AccessDeniedException
import java.nio.file.AccessMode
import java.nio.file.CopyOption
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import com.wisso.wizefiles.storage.path.toAppPathOrNull
import com.wisso.wizefiles.provider.archive.legacy.toArchiveAppPath
import com.wisso.wizefiles.storage.path.AppPath
import java.nio.file.ProviderMismatchException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileAttributeView
import java.nio.file.spi.FileSystemProvider
import com.wisso.wizefiles.provider.common.ByteStringPath
import com.wisso.wizefiles.provider.common.FileSystemCache
import com.wisso.wizefiles.provider.common.PathListDirectoryStream
import com.wisso.wizefiles.provider.common.PathObservable
import com.wisso.wizefiles.provider.common.PathObservableProvider
import com.wisso.wizefiles.provider.common.ReadOnlyFileSystemException
import com.wisso.wizefiles.provider.common.Searchable
import com.wisso.wizefiles.provider.common.WalkFileTreeSearchable
import com.wisso.wizefiles.provider.common.decodedPathByteString
import com.wisso.wizefiles.provider.common.decodedQueryByteString
import com.wisso.wizefiles.provider.common.isSameFile
import com.wisso.wizefiles.provider.common.toAccessModes
import com.wisso.wizefiles.provider.common.toByteString
import com.wisso.wizefiles.provider.common.toOpenOptions
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.nio.file.DirectoryStream

object ArchiveFileSystemProvider : FileSystemProvider(), PathObservableProvider, Searchable {
    private const val SCHEME = "archive"

    private val fileSystems = FileSystemCache<AppPath, ArchiveFileSystem>()

    override fun getScheme(): String = SCHEME

    override fun newFileSystem(uri: URI, env: Map<String, *>): FileSystem {
        uri.requireSameScheme()
        val archiveFile = uri.archiveAppPath
        return fileSystems.create(archiveFile) { newFileSystem(archiveFile) }
    }

    override fun newFileSystem(file: Path, env: Map<String, *>): FileSystem =
        newFileSystem(file.toArchiveAppPath())

    internal fun getOrNewFileSystem(archiveFile: Path): ArchiveFileSystem =
        getOrNewFileSystem(archiveFile.toArchiveAppPath())

    internal fun getOrNewFileSystem(archiveFile: AppPath): ArchiveFileSystem =
        fileSystems.getOrCreate(archiveFile) { newFileSystem(archiveFile) }

    private fun newFileSystem(archiveFile: AppPath): ArchiveFileSystem =
        ArchiveFileSystem(this, archiveFile)

    override fun getFileSystem(uri: URI): FileSystem {
        uri.requireSameScheme()
        val archiveFile = uri.archiveAppPath
        return fileSystems[archiveFile]
    }

    internal fun removeFileSystem(fileSystem: ArchiveFileSystem) {
        fileSystems.remove(fileSystem.archiveFile, fileSystem)
    }

    override fun getPath(uri: URI): Path {
        uri.requireSameScheme()
        val archiveFile = uri.archiveAppPath
        val path = uri.decodedQueryByteString
            ?: throw IllegalArgumentException("URI must have a query")
        return getOrNewFileSystem(archiveFile).getPath(path)
    }

    private fun URI.requireSameScheme() {
        val scheme = scheme
        require(scheme == SCHEME) { "URI scheme $scheme must be $SCHEME" }
    }

    private val URI.archiveAppPath: AppPath
        get() {
            val path = decodedPathByteString
                ?: throw IllegalArgumentException("URI must have a path")
            val archiveUri = URI.create(path.toString().drop(1))
            return archiveUri.toString().toAppPathOrNull()
                ?: throw IllegalArgumentException("Unsupported archive URI: $archiveUri")
        }

    @Throws(IOException::class)
    override fun newInputStream(file: Path, vararg options: OpenOption): InputStream {
        file as? ArchivePath ?: throw ProviderMismatchException(file.toString())
        options.toOpenOptions().checkForArchive()
        return file.fileSystem.newInputStream(file)
    }

    override fun newFileChannel(
        file: Path,
        options: Set<OpenOption>,
        vararg attributes: FileAttribute<*>
    ): FileChannel {
        file as? ArchivePath ?: throw ProviderMismatchException(file.toString())
        options.toOpenOptions().checkForArchive()
        if (attributes.isNotEmpty()) {
            throw UnsupportedOperationException(attributes.contentToString())
        }
        throw UnsupportedOperationException()
    }

    override fun newByteChannel(
        file: Path,
        options: Set<OpenOption>,
        vararg attributes: FileAttribute<*>
    ): SeekableByteChannel {
        file as? ArchivePath ?: throw ProviderMismatchException(file.toString())
        options.toOpenOptions().checkForArchive()
        if (attributes.isNotEmpty()) {
            throw UnsupportedOperationException(attributes.contentToString())
        }
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun newDirectoryStream(
        directory: Path,
        filter: DirectoryStream.Filter<in Path>
    ): DirectoryStream<Path> {
        directory as? ArchivePath ?: throw ProviderMismatchException(directory.toString())
        val children = directory.fileSystem.getDirectoryChildren(directory)
        return PathListDirectoryStream(children, filter)
    }

    @Throws(IOException::class)
    override fun createDirectory(directory: Path, vararg attributes: FileAttribute<*>) {
        directory as? ArchivePath ?: throw ProviderMismatchException(directory.toString())
        throw ReadOnlyFileSystemException(directory.toString())
    }

    @Throws(IOException::class)
    override fun createSymbolicLink(link: Path, target: Path, vararg attributes: FileAttribute<*>) {
        link as? ArchivePath ?: throw ProviderMismatchException(link.toString())
        when (target) {
            is ArchivePath, is ByteStringPath -> {}
            else -> throw ProviderMismatchException(target.toString())
        }
        throw ReadOnlyFileSystemException(link.toString(), target.toString(), null)
    }

    @Throws(IOException::class)
    override fun createLink(link: Path, existing: Path) {
        link as? ArchivePath ?: throw ProviderMismatchException(link.toString())
        existing as? ArchivePath ?: throw ProviderMismatchException(existing.toString())
        throw ReadOnlyFileSystemException(link.toString(), existing.toString(), null)
    }

    @Throws(IOException::class)
    override fun delete(path: Path) {
        path as? ArchivePath ?: throw ProviderMismatchException(path.toString())
        throw ReadOnlyFileSystemException(path.toString())
    }

    @Throws(IOException::class)
    override fun readSymbolicLink(link: Path): Path {
        link as? ArchivePath ?: throw ProviderMismatchException(link.toString())
        val target = link.fileSystem.readSymbolicLink(link)
        return ByteStringPath(target.toByteString())
    }

    @Throws(IOException::class)
    override fun copy(source: Path, target: Path, vararg options: CopyOption) {
        source as? ArchivePath ?: throw ProviderMismatchException(source.toString())
        target as? ArchivePath ?: throw ProviderMismatchException(target.toString())
        throw ReadOnlyFileSystemException(source.toString(), target.toString(), null)
    }

    @Throws(IOException::class)
    override fun move(source: Path, target: Path, vararg options: CopyOption) {
        source as? ArchivePath ?: throw ProviderMismatchException(source.toString())
        target as? ArchivePath ?: throw ProviderMismatchException(target.toString())
        throw ReadOnlyFileSystemException(source.toString(), target.toString(), null)
    }

    @Throws(IOException::class)
    override fun isSameFile(path: Path, path2: Path): Boolean {
        path as? ArchivePath ?: throw ProviderMismatchException(path.toString())
        if (path == path2) {
            return true
        }
        if (path2 !is ArchivePath) {
            return false
        }
        val fileSystem = path.fileSystem
        if (fileSystem.archiveFile != path2.fileSystem.archiveFile) {
            return false
        }
        return path == fileSystem.getPath(path2.toString())
    }

    override fun isHidden(path: Path): Boolean {
        path as? ArchivePath ?: throw ProviderMismatchException(path.toString())
        return false
    }

    override fun getFileStore(path: Path): FileStore {
        path as? ArchivePath ?: throw ProviderMismatchException(path.toString())
        val archiveFile = path.fileSystem.archiveFile
        return ArchiveFileStore(archiveFile)
    }

    @Throws(IOException::class)
    override fun checkAccess(path: Path, vararg modes: AccessMode) {
        path as? ArchivePath ?: throw ProviderMismatchException(path.toString())
        val accessModes = modes.toAccessModes()
        path.fileSystem.getEntry(path)
        if (accessModes.write || accessModes.execute) {
            throw AccessDeniedException(path.toString())
        }
    }

    override fun <V : FileAttributeView> getFileAttributeView(
        path: Path,
        type: Class<V>,
        vararg options: LinkOption
    ): V? {
        path as? ArchivePath ?: throw ProviderMismatchException(path.toString())
        if (!supportsFileAttributeView(type)) {
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return getFileAttributeView(path) as V
    }

    internal fun supportsFileAttributeView(type: Class<out FileAttributeView>): Boolean =
        type.isAssignableFrom(ArchiveFileAttributeView::class.java)

    @Throws(IOException::class)
    override fun <A : BasicFileAttributes> readAttributes(
        path: Path,
        type: Class<A>,
        vararg options: LinkOption
    ): A {
        path as? ArchivePath ?: throw ProviderMismatchException(path.toString())
        if (!type.isAssignableFrom(ArchiveFileAttributes::class.java)) {
            throw UnsupportedOperationException(type.toString())
        }
        @Suppress("UNCHECKED_CAST")
        return getFileAttributeView(path).readAttributes() as A
    }

    private fun getFileAttributeView(path: ArchivePath): ArchiveFileAttributeView =
        ArchiveFileAttributeView(path)

    override fun readAttributes(
        path: Path,
        attributes: String,
        vararg options: LinkOption
    ): Map<String, Any> {
        path as? ArchivePath ?: throw ProviderMismatchException(path.toString())
        throw UnsupportedOperationException()
    }

    override fun setAttribute(
        path: Path,
        attribute: String,
        value: Any,
        vararg options: LinkOption
    ) {
        path as? ArchivePath ?: throw ProviderMismatchException(path.toString())
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun observe(path: Path, intervalMillis: Long): PathObservable {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun search(
        directory: Path,
        query: String,
        intervalMillis: Long,
        listener: (List<Path>) -> Unit
    ) {
        directory as? ArchivePath ?: throw ProviderMismatchException(directory.toString())
        WalkFileTreeSearchable.search(directory, query, intervalMillis, listener)
    }
}
