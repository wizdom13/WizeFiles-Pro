package com.wisso.wizefiles.provider.sftp

import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.AccessMode
import java.nio.file.CopyOption
import java.nio.file.DirectoryStream
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.FileSystemAlreadyExistsException
import java.nio.file.FileSystemNotFoundException
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.ProviderMismatchException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileAttributeView
import java.nio.file.spi.FileSystemProvider
import com.wisso.wizefiles.provider.common.ByteStringPath
import com.wisso.wizefiles.provider.common.PathListDirectoryStream
import com.wisso.wizefiles.provider.common.PathObservable
import com.wisso.wizefiles.provider.common.PathObservableProvider
import com.wisso.wizefiles.provider.common.PosixFileMode
import com.wisso.wizefiles.provider.common.Searchable
import com.wisso.wizefiles.provider.common.WalkFileTreeSearchable
import com.wisso.wizefiles.provider.common.WatchServicePathObservable
import com.wisso.wizefiles.provider.common.decodedPathByteString
import com.wisso.wizefiles.provider.common.toAccessModes
import com.wisso.wizefiles.provider.common.toByteString
import com.wisso.wizefiles.provider.common.toCopyOptions
import com.wisso.wizefiles.provider.common.toLinkOptions
import com.wisso.wizefiles.provider.common.toOpenOptions
import com.wisso.wizefiles.provider.sftp.client.Authority
import com.wisso.wizefiles.provider.sftp.client.SftpClient
import com.wisso.wizefiles.provider.sftp.client.SftpClientException
import com.wisso.wizefiles.provider.sftp.client.SecurityProviderHelper
import com.wisso.wizefiles.util.enumSetOf
import net.schmizz.sshj.sftp.OpenMode
import java.io.IOException
import java.net.URI

object SftpFileSystemProvider : FileSystemProvider(), PathObservableProvider, Searchable {
    private const val SCHEME = "sftp"

    private val HIDDEN_FILE_NAME_PREFIX = ".".toByteString()

    private val fileSystems = mutableMapOf<Authority, SftpFileSystem>()

    private val lock = Any()

    init {
        SecurityProviderHelper.init()
    }

    override fun getScheme(): String = SCHEME

    override fun newFileSystem(uri: URI, env: Map<String, *>): FileSystem {
        uri.requireSameScheme()
        val authority = uri.sftpAuthority
        synchronized(lock) {
            if (fileSystems[authority] != null) {
                throw FileSystemAlreadyExistsException(authority.toString())
            }
            return newFileSystemLocked(authority)
        }
    }

    internal fun getOrNewFileSystem(authority: Authority): SftpFileSystem =
        synchronized(lock) { fileSystems[authority] ?: newFileSystemLocked(authority) }

    private fun newFileSystemLocked(authority: Authority): SftpFileSystem {
        val fileSystem = SftpFileSystem(this, authority)
        fileSystems[authority] = fileSystem
        return fileSystem
    }

    override fun getFileSystem(uri: URI): FileSystem {
        uri.requireSameScheme()
        val authority = uri.sftpAuthority
        return synchronized(lock) { fileSystems[authority] }
            ?: throw FileSystemNotFoundException(authority.toString())
    }

    internal fun removeFileSystem(fileSystem: SftpFileSystem) {
        val authority = fileSystem.authority
        synchronized(lock) { fileSystems.remove(authority) }
    }

    override fun getPath(uri: URI): Path {
        uri.requireSameScheme()
        val authority = uri.sftpAuthority
        val path = uri.decodedPathByteString
            ?: throw IllegalArgumentException("URI must have a path")
        return getOrNewFileSystem(authority).getPath(path)
    }

    private fun URI.requireSameScheme() {
        val scheme = scheme
        require(scheme == SCHEME) { "URI scheme $scheme must be $SCHEME" }
    }

    private val URI.sftpAuthority: Authority
        get() {
            val port = if (port != -1) port else Authority.DEFAULT_PORT
            val username = userInfo.orEmpty()
            return Authority(host, port, username)
        }

    @Throws(IOException::class)
    override fun newFileChannel(
        file: Path,
        options: Set<OpenOption>,
        vararg attributes: FileAttribute<*>
    ): FileChannel {
        file as? SftpPath ?: throw ProviderMismatchException(file.toString())
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun newByteChannel(
        file: Path,
        options: Set<OpenOption>,
        vararg attributes: FileAttribute<*>
    ): SeekableByteChannel {
        file as? SftpPath ?: throw ProviderMismatchException(file.toString())
        val openOptions = options.toOpenOptions()
        val flags = openOptions.toSftpFlags()
        val sftpAttributes = (PosixFileMode.fromAttributes(attributes)
            ?: PosixFileMode.CREATE_FILE_DEFAULT).toSftpAttributes()
        return try {
            SftpClient.openByteChannel(file, flags, sftpAttributes)
        } catch (e: SftpClientException) {
            throw e.toFileSystemException(file.toString())
        }
    }

    @Throws(IOException::class)
    override fun newDirectoryStream(
        directory: Path,
        filter: DirectoryStream.Filter<in Path>
    ): DirectoryStream<Path> {
        directory as? SftpPath ?: throw ProviderMismatchException(directory.toString())
        val paths = try {
            @Suppress("UNCHECKED_CAST")
            SftpClient.scandir(directory) as List<Path>
        } catch (e: SftpClientException) {
            throw e.toFileSystemException(directory.toString())
        }
        return PathListDirectoryStream(paths, filter)
    }

    @Throws(IOException::class)
    override fun createDirectory(directory: Path, vararg attributes: FileAttribute<*>) {
        directory as? SftpPath ?: throw ProviderMismatchException(directory.toString())
        val sftpAttributes = (PosixFileMode.fromAttributes(attributes)
            ?: PosixFileMode.CREATE_DIRECTORY_DEFAULT).toSftpAttributes()
        try {
            SftpClient.mkdir(directory, sftpAttributes)
        } catch (e: SftpClientException) {
            throw e.toFileSystemException(directory.toString())
        }
    }

    override fun createSymbolicLink(link: Path, target: Path, vararg attributes: FileAttribute<*>) {
        link as? SftpPath ?: throw ProviderMismatchException(link.toString())
        val targetString = when (target) {
            is SftpPath -> target.toString()
            is ByteStringPath -> target.toString()
            else -> throw ProviderMismatchException(target.toString())
        }
        if (attributes.isNotEmpty()) {
            throw UnsupportedOperationException(attributes.contentToString())
        }
        try {
            SftpClient.symlink(link, targetString)
        } catch (e: SftpClientException) {
            throw e.toFileSystemException(link.toString(), targetString)
        }
    }

    override fun createLink(link: Path, existing: Path) {
        link as? SftpPath ?: throw ProviderMismatchException(link.toString())
        existing as? SftpPath ?: throw ProviderMismatchException(existing.toString())
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun delete(path: Path) {
        path as? SftpPath ?: throw ProviderMismatchException(path.toString())
        try {
            SftpClient.remove(path)
        } catch (e: SftpClientException) {
            throw e.toFileSystemException(path.toString())
        }
    }

    override fun readSymbolicLink(link: Path): Path {
        link as? SftpPath ?: throw ProviderMismatchException(link.toString())
        val target = try {
            SftpClient.readlink(link)
        } catch (e: SftpClientException) {
            throw e.toFileSystemException(link.toString())
        }
        return ByteStringPath(target.toByteString())
    }

    @Throws(IOException::class)
    override fun copy(source: Path, target: Path, vararg options: CopyOption) {
        source as? SftpPath ?: throw ProviderMismatchException(source.toString())
        target as? SftpPath ?: throw ProviderMismatchException(target.toString())
        val copyOptions = options.toCopyOptions()
        SftpCopyMove.copy(source, target, copyOptions)
    }

    @Throws(IOException::class)
    override fun move(source: Path, target: Path, vararg options: CopyOption) {
        source as? SftpPath ?: throw ProviderMismatchException(source.toString())
        target as? SftpPath ?: throw ProviderMismatchException(target.toString())
        val copyOptions = options.toCopyOptions()
        SftpCopyMove.move(source, target, copyOptions)
    }

    override fun isSameFile(path: Path, path2: Path): Boolean {
        path as? SftpPath ?: throw ProviderMismatchException(path.toString())
        return path == path2
    }

    override fun isHidden(path: Path): Boolean {
        path as? SftpPath ?: throw ProviderMismatchException(path.toString())
        val fileName = path.fileNameByteString ?: return false
        return fileName.startsWith(HIDDEN_FILE_NAME_PREFIX)
    }

    override fun getFileStore(path: Path): FileStore {
        path as? SftpPath ?: throw ProviderMismatchException(path.toString())
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun checkAccess(path: Path, vararg modes: AccessMode) {
        path as? SftpPath ?: throw ProviderMismatchException(path.toString())
        val accessModes = modes.toAccessModes()
        if (accessModes.execute) {
            throw UnsupportedOperationException(AccessMode.EXECUTE.toString())
        }
        val flags = enumSetOf<OpenMode>().apply {
            if (accessModes.read) {
                this += OpenMode.READ
            }
            if (accessModes.write) {
                this += OpenMode.WRITE
            }
        }
        try {
            SftpClient.access(path, flags)
        } catch (e: SftpClientException) {
            throw e.toFileSystemException(path.toString())
        }
    }

    override fun <V : FileAttributeView> getFileAttributeView(
        path: Path,
        type: Class<V>,
        vararg options: LinkOption
    ): V? {
        if (!supportsFileAttributeView(type)) {
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return getFileAttributeView(path, *options) as V
    }

    internal fun supportsFileAttributeView(type: Class<out FileAttributeView>): Boolean =
        type.isAssignableFrom(SftpFileAttributeView::class.java)

    @Throws(IOException::class)
    override fun <A : BasicFileAttributes> readAttributes(
        path: Path,
        type: Class<A>,
        vararg options: LinkOption
    ): A {
        if (!type.isAssignableFrom(BasicFileAttributes::class.java)) {
            throw UnsupportedOperationException(type.toString())
        }
        @Suppress("UNCHECKED_CAST")
        return getFileAttributeView(path, *options).readAttributes() as A
    }

    private fun getFileAttributeView(path: Path, vararg options: LinkOption): SftpFileAttributeView {
        path as? SftpPath ?: throw ProviderMismatchException(path.toString())
        val linkOptions = options.toLinkOptions()
        return SftpFileAttributeView(path, linkOptions.noFollowLinks)
    }

    override fun readAttributes(
        path: Path,
        attributes: String,
        vararg options: LinkOption
    ): Map<String, Any> {
        path as? SftpPath ?: throw ProviderMismatchException(path.toString())
        throw UnsupportedOperationException()
    }

    override fun setAttribute(
        path: Path,
        attribute: String,
        value: Any,
        vararg options: LinkOption
    ) {
        path as? SftpPath ?: throw ProviderMismatchException(path.toString())
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun observe(path: Path, intervalMillis: Long): PathObservable {
        path as? SftpPath ?: throw ProviderMismatchException(path.toString())
        return WatchServicePathObservable(path, intervalMillis)
    }

    @Throws(IOException::class)
    override fun search(
        directory: Path,
        query: String,
        intervalMillis: Long,
        listener: (List<Path>) -> Unit
    ) {
        directory as? SftpPath ?: throw ProviderMismatchException(directory.toString())
        WalkFileTreeSearchable.search(directory, query, intervalMillis, listener)
    }
}
