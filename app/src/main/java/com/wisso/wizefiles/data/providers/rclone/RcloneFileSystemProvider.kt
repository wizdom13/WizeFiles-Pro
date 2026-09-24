package com.wisso.wizefiles.provider.rclone

import com.wisso.wizefiles.provider.common.CommittedPathStateProvider
import com.wisso.wizefiles.provider.common.PathListDirectoryStream
import com.wisso.wizefiles.provider.common.Searchable
import com.wisso.wizefiles.provider.common.WalkFileTreeSearchable
import com.wisso.wizefiles.provider.common.decodedPathByteString
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.channels.SeekableByteChannel
import java.nio.file.AccessMode
import java.nio.file.CopyOption
import java.nio.file.DirectoryStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.FileSystemAlreadyExistsException
import java.nio.file.FileSystemNotFoundException
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.ProviderMismatchException
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileAttributeView
import java.nio.file.spi.FileSystemProvider

// Deliberately not a PathObservableProvider. The generic watcher polls every child
// with a separate remote stat call each second, which creates a request storm when
// a cloud provider or the network is unavailable. Cloud paths refresh on entry and
// explicit user refresh instead.
object RcloneFileSystemProvider :
    FileSystemProvider(),
    Searchable,
    CommittedPathStateProvider {
    const val SCHEME = "rclone"

    private val fileSystems = mutableMapOf<String, RcloneFileSystem>()
    private val lock = Any()
    private val directoryCache = mutableMapOf<String, List<RcloneListedPath>>()
    private val directoryCacheLock = Any()

    override fun getScheme(): String = SCHEME

    override fun newFileSystem(uri: URI, env: Map<String, *>): FileSystem {
        uri.requireRcloneScheme()
        val remoteName = uri.requireRemoteName()
        synchronized(lock) {
            if (fileSystems.containsKey(remoteName)) {
                throw FileSystemAlreadyExistsException(remoteName)
            }
            return newFileSystemLocked(remoteName)
        }
    }

    internal fun getOrNewFileSystem(remoteName: String): RcloneFileSystem =
        synchronized(lock) { fileSystems[remoteName] ?: newFileSystemLocked(remoteName) }

    private fun newFileSystemLocked(remoteName: String): RcloneFileSystem =
        RcloneFileSystem(this, remoteName).also { fileSystems[remoteName] = it }

    override fun getFileSystem(uri: URI): FileSystem {
        uri.requireRcloneScheme()
        val remoteName = uri.requireRemoteName()
        return synchronized(lock) { fileSystems[remoteName] }
            ?: throw FileSystemNotFoundException(remoteName)
    }

    internal fun removeFileSystem(fileSystem: RcloneFileSystem) {
        synchronized(lock) { fileSystems.remove(fileSystem.remoteName) }
    }

    override fun getPath(uri: URI): Path {
        uri.requireRcloneScheme()
        val path = uri.decodedPathByteString
            ?: throw IllegalArgumentException("rclone URI must have a path")
        return getOrNewFileSystem(uri.requireRemoteName()).getPath(path)
    }

    override fun newInputStream(file: Path, vararg options: OpenOption): InputStream {
        val path = file.requireRclonePath()
        if (options.any { it != StandardOpenOption.READ && it != LinkOption.NOFOLLOW_LINKS }) {
            throw UnsupportedOperationException(options.contentToString())
        }
        RcloneUploadCache.openInput(path)?.let { return it }
        requireExistingFile(path)
        return RcloneStaging.openInput(path)
    }

    override fun newOutputStream(file: Path, vararg options: OpenOption): OutputStream {
        val path = file.requireRclonePath()
        val normalizedOptions = options.toMutableSet().apply {
            if (isEmpty()) {
                add(StandardOpenOption.CREATE)
                add(StandardOpenOption.TRUNCATE_EXISTING)
            }
        }
        val existing = RcloneEngine.stat(path.remoteName, path.remotePath)
        if (StandardOpenOption.CREATE_NEW in normalizedOptions && existing != null) {
            throw FileAlreadyExistsException(path.toString())
        }
        if (
            StandardOpenOption.CREATE !in normalizedOptions &&
            StandardOpenOption.CREATE_NEW !in normalizedOptions &&
            existing == null
        ) {
            throw NoSuchFileException(path.toString())
        }
        return RcloneStaging.openOutput(path, StandardOpenOption.APPEND in normalizedOptions)
    }

    override fun newByteChannel(
        file: Path,
        options: Set<OpenOption>,
        vararg attributes: FileAttribute<*>
    ): SeekableByteChannel {
        val path = file.requireRclonePath()
        if (attributes.isNotEmpty()) {
            throw UnsupportedOperationException(attributes.contentToString())
        }
        val existing = RcloneEngine.stat(path.remoteName, path.remotePath)
        if (StandardOpenOption.CREATE_NEW in options && existing != null) {
            throw FileAlreadyExistsException(path.toString())
        }
        val creates = StandardOpenOption.CREATE in options || StandardOpenOption.CREATE_NEW in options
        if (existing == null && !creates && StandardOpenOption.WRITE in options) {
            throw NoSuchFileException(path.toString())
        }
        if (existing == null && options.none { it == StandardOpenOption.WRITE || creates }) {
            throw NoSuchFileException(path.toString())
        }
        return RcloneStaging.openChannel(path, options)
    }

    override fun newDirectoryStream(
        directory: Path,
        filter: DirectoryStream.Filter<in Path>
    ): DirectoryStream<Path> {
        val children = listWithAttributes(directory, applyPendingMutations = false).map { it.path }
        return PathListDirectoryStream(children, filter)
    }

    internal fun listWithAttributes(
        directory: Path,
        applyPendingMutations: Boolean = false
    ): List<RcloneListedPath> {
        val path = directory.requireRclonePath()
        if (applyPendingMutations) {
            val pendingUploads = RcloneUploadCache.pendingChildren(path)
            val hasPendingDeletes = hasPendingRcloneDeletes(path)
            if (pendingUploads.isNotEmpty() || hasPendingDeletes) {
                val cached = synchronized(directoryCacheLock) {
                    directoryCache[path.directoryCacheKey].orEmpty()
                }
                // Rclone serializes RPC calls. Avoid waiting behind a remote mutation and
                // overlay local uploads/deletion tombstones on the last successful listing.
                return filterPendingRcloneDeletes(
                    mergeRcloneListedPaths(cached, pendingUploads)
                )
            }
        }

        ensureRequestIsActive()
        val entries = RcloneEngine.list(path.remoteName, path.remotePath)
        ensureRequestIsActive()
        val root = path.fileSystem.rootDirectories.first() as RclonePath
        val listedPaths = entries.map { entry ->
            ensureRequestIsActive()
            val entryPath = entry.path.ifBlank { entry.name }
            val childPath = root.resolve(entryPath)
            RcloneListedPath(
                path = childPath,
                attributes = RcloneFileAttributes(
                    path = childPath.toString(),
                    directory = entry.isDirectory,
                    length = entry.size,
                    modifiedAt = entry.modifiedAt
                )
            )
        }
        synchronized(directoryCacheLock) {
            directoryCache[path.directoryCacheKey] = listedPaths
        }
        if (!applyPendingMutations) {
            return listedPaths
        }
        return filterPendingRcloneDeletes(
            mergeRcloneListedPaths(listedPaths, RcloneUploadCache.pendingChildren(path))
        )
    }

    internal fun cacheCompletedUpload(entry: RcloneListedPath) {
        val path = entry.path as? RclonePath ?: return
        val parent = path.parent as? RclonePath ?: return
        synchronized(directoryCacheLock) {
            val key = parent.directoryCacheKey
            directoryCache[key] = mergeRcloneListedPaths(directoryCache[key].orEmpty(), listOf(entry))
        }
    }

    internal fun cacheCompletedDelete(path: RclonePath) {
        val deletedPath = path.toAbsolutePath().normalize()
        val deletedDirectoryKey = path.directoryCacheKey
        val deletedDirectoryPrefix = if (deletedDirectoryKey.endsWith('/')) {
            deletedDirectoryKey
        } else {
            "$deletedDirectoryKey/"
        }
        synchronized(directoryCacheLock) {
            for (key in directoryCache.keys.toList()) {
                if (key == deletedDirectoryKey || key.startsWith(deletedDirectoryPrefix)) {
                    directoryCache.remove(key)
                    continue
                }
                directoryCache[key] = directoryCache[key].orEmpty().filterNot { entry ->
                    val entryPath = entry.path.toAbsolutePath().normalize()
                    entryPath == deletedPath || entryPath.startsWith(deletedPath)
                }
            }
        }
    }

    override fun createDirectory(directory: Path, vararg attributes: FileAttribute<*>) =
        createDirectoryWithOperations(directory, attributes, RcloneMutationOperations.DEFAULT)

    internal fun createDirectoryWithOperations(
        directory: Path,
        attributes: Array<out FileAttribute<*>>,
        operations: RcloneMutationOperations
    ) {
        ensureRequestIsActive()
        val path = directory.requireRclonePath()
        if (attributes.isNotEmpty()) {
            throw UnsupportedOperationException(attributes.contentToString())
        }
        if (operations.stat(path.remoteName, path.remotePath) != null) {
            throw FileAlreadyExistsException(path.toString())
        }
        ensureRequestIsActive()
        operations.createDirectory(path.remoteName, path.remotePath)
    }

    override fun delete(path: Path) =
        deleteWithOperations(path, RcloneMutationOperations.DEFAULT)

    internal fun deleteWithOperations(path: Path, operations: RcloneMutationOperations) {
        ensureRequestIsActive()
        val rclonePath = path.requireRclonePath()
        val entry = operations.stat(rclonePath.remoteName, rclonePath.remotePath)
            ?: throw NoSuchFileException(path.toString())
        ensureRequestIsActive()
        if (entry.isDirectory) {
            operations.deleteEmptyDirectory(rclonePath.remoteName, rclonePath.remotePath)
        } else {
            operations.deleteFile(rclonePath.remoteName, rclonePath.remotePath)
        }
        completeRcloneDelete(rclonePath)
    }

    override fun copy(source: Path, target: Path, vararg options: CopyOption) =
        copyWithOperations(source, target, options, RcloneMutationOperations.DEFAULT)

    internal fun copyWithOperations(
        source: Path,
        target: Path,
        options: Array<out CopyOption>,
        operations: RcloneMutationOperations
    ) {
        ensureRequestIsActive()
        val sourcePath = source.requireRclonePath()
        val targetPath = target.requireRclonePath()
        val replaceExisting = StandardCopyOption.REPLACE_EXISTING in options
        val targetEntry = operations.stat(targetPath.remoteName, targetPath.remotePath)
        if (targetEntry != null && !replaceExisting) {
            throw FileAlreadyExistsException(target.toString())
        }
        ensureRequestIsActive()
        val sourceEntry = operations.stat(sourcePath.remoteName, sourcePath.remotePath)
            ?: throw NoSuchFileException(source.toString())
        if (sourceEntry.isDirectory) {
            throw UnsupportedOperationException("Directory copy is handled by the transfer engine")
        }
        ensureRequestIsActive()
        operations.copy(
            sourcePath.remoteName,
            sourcePath.remotePath,
            targetPath.remoteName,
            targetPath.remotePath
        )
    }

    override fun move(source: Path, target: Path, vararg options: CopyOption) =
        moveWithOperations(source, target, options, RcloneMutationOperations.DEFAULT)

    internal fun moveWithOperations(
        source: Path,
        target: Path,
        options: Array<out CopyOption>,
        operations: RcloneMutationOperations
    ) {
        ensureRequestIsActive()
        val sourcePath = source.requireRclonePath()
        val targetPath = target.requireRclonePath()
        val replaceExisting = StandardCopyOption.REPLACE_EXISTING in options
        if (
            operations.stat(targetPath.remoteName, targetPath.remotePath) != null &&
            !replaceExisting
        ) {
            throw FileAlreadyExistsException(target.toString())
        }
        ensureRequestIsActive()
        val sourceEntry = operations.stat(sourcePath.remoteName, sourcePath.remotePath)
            ?: throw NoSuchFileException(source.toString())
        if (sourceEntry.isDirectory) {
            throw UnsupportedOperationException("Directory move is handled by the transfer engine")
        }
        ensureRequestIsActive()
        operations.move(
            sourcePath.remoteName,
            sourcePath.remotePath,
            targetPath.remoteName,
            targetPath.remotePath
        )
    }

    override fun isSameFile(path: Path, path2: Path): Boolean =
        path.requireRclonePath().toAbsolutePath().normalize() ==
            path2.requireRclonePath().toAbsolutePath().normalize()

    override fun isHidden(path: Path): Boolean =
        path.requireRclonePath().fileName?.toString()?.startsWith('.') == true

    override fun getFileStore(path: Path): FileStore =
        throw UnsupportedOperationException()

    override fun checkAccess(path: Path, vararg modes: AccessMode) {
        if (!existsInCommittedStorage(path)) {
            throw NoSuchFileException(path.toString())
        }
    }

    override fun existsInCommittedStorage(
        path: Path,
        vararg options: LinkOption
    ): Boolean = rclonePathExistsRemotely(path.requireRclonePath(), RcloneEngine::stat)

    override fun <V : FileAttributeView> getFileAttributeView(
        path: Path,
        type: Class<V>,
        vararg options: LinkOption
    ): V? = null

    override fun <A : BasicFileAttributes> readAttributes(
        path: Path,
        type: Class<A>,
        vararg options: LinkOption
    ): A {
        if (!type.isAssignableFrom(RcloneFileAttributes::class.java) &&
            type != BasicFileAttributes::class.java) {
            throw UnsupportedOperationException(type.name)
        }
        val rclonePath = path.requireRclonePath()
        val attributes = RcloneUploadCache.attributes(rclonePath) ?: run {
            val entry = RcloneEngine.stat(rclonePath.remoteName, rclonePath.remotePath)
                ?: throw NoSuchFileException(path.toString())
            RcloneFileAttributes(
                path = rclonePath.toString(),
                directory = entry.isDirectory,
                length = entry.size,
                modifiedAt = entry.modifiedAt
            )
        }
        @Suppress("UNCHECKED_CAST")
        return attributes as A
    }

    override fun readAttributes(
        path: Path,
        attributes: String,
        vararg options: LinkOption
    ): Map<String, Any> = throw UnsupportedOperationException(attributes)

    override fun setAttribute(
        path: Path,
        attribute: String,
        value: Any,
        vararg options: LinkOption
    ) {
        throw UnsupportedOperationException(attribute)
    }

    override fun search(
        directory: Path,
        query: String,
        intervalMillis: Long,
        listener: (List<Path>) -> Unit
    ) {
        WalkFileTreeSearchable.search(
            directory.requireRclonePath(),
            query,
            intervalMillis,
            listener
        )
    }

    private fun requireExistingFile(path: RclonePath) {
        val entry = RcloneEngine.stat(path.remoteName, path.remotePath)
            ?: throw NoSuchFileException(path.toString())
        if (entry.isDirectory) {
            throw IOException("Cannot open a directory as a file: $path")
        }
    }

    private fun URI.requireRcloneScheme() {
        require(scheme == SCHEME) { "URI scheme $scheme must be $SCHEME" }
    }

    private fun URI.requireRemoteName(): String =
        host?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("rclone URI must include a remote name")

    private fun Path.requireRclonePath(): RclonePath =
        this as? RclonePath ?: throw ProviderMismatchException(toString())

    private fun ensureRequestIsActive() {
        if (Thread.currentThread().isInterrupted) {
            throw java.io.InterruptedIOException("rclone directory request cancelled")
        }
    }

    private val RclonePath.directoryCacheKey: String
        get() = "$remoteName\u0000$remotePath"
}

/**
 * Pending upload plans are display-only. Filesystem access and conflict detection must use the
 * committed remote state so a copy job never collides with its own placeholder.
 */
internal fun rclonePathExistsRemotely(
    path: RclonePath,
    stat: (remoteName: String, remotePath: String) -> RcloneEntry?
): Boolean = stat(path.remoteName, path.remotePath) != null

internal data class RcloneListedPath(
    val path: Path,
    val attributes: BasicFileAttributes
)

fun createRcloneRootPath(remoteName: String): Path =
    RcloneFileSystemProvider.getOrNewFileSystem(remoteName).rootDirectory


internal fun mergeRcloneListedPaths(
    base: List<RcloneListedPath>,
    overlay: List<RcloneListedPath>
): List<RcloneListedPath> {
    val entries = linkedMapOf<String, RcloneListedPath>()
    (base + overlay).forEach { entry ->
        entries[entry.path.toAbsolutePath().normalize().toString()] = entry
    }
    return entries.values.toList()
}
