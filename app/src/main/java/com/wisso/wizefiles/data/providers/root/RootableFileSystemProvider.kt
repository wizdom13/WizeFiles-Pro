package com.wisso.wizefiles.provider.root

import com.wisso.wizefiles.provider.common.PathObservable
import com.wisso.wizefiles.provider.common.PathObservableProvider
import com.wisso.wizefiles.provider.common.Searchable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.channels.FileChannel
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

abstract class RootableFileSystemProvider(
    createLocal: (FileSystemProvider) -> FileSystemProvider,
    createRoot: (FileSystemProvider) -> FileSystemProvider
) : FileSystemProvider(), PathObservableProvider, Searchable {
    protected open val localProvider: FileSystemProvider = createLocal(this)
    protected open val rootProvider: FileSystemProvider = createRoot(this)

    final override fun getScheme(): String = localProvider.scheme
    final override fun newFileSystem(uri: URI, env: Map<String, *>): FileSystem =
        localProvider.newFileSystem(uri, env)
    final override fun getFileSystem(uri: URI): FileSystem = localProvider.getFileSystem(uri)
    final override fun getPath(uri: URI): Path = localProvider.getPath(uri)

    final override fun newInputStream(path: Path, vararg options: OpenOption): InputStream =
        route(path) { it.newInputStream(path, *options) }

    final override fun newOutputStream(path: Path, vararg options: OpenOption): OutputStream =
        route(path) { it.newOutputStream(path, *options) }

    final override fun newFileChannel(
        path: Path,
        options: Set<OpenOption>,
        vararg attributes: FileAttribute<*>
    ): FileChannel = route(path) { it.newFileChannel(path, options, *attributes) }

    final override fun newByteChannel(
        path: Path,
        options: Set<OpenOption>,
        vararg attributes: FileAttribute<*>
    ): SeekableByteChannel = route(path) { it.newByteChannel(path, options, *attributes) }

    final override fun newDirectoryStream(
        directory: Path,
        filter: DirectoryStream.Filter<in Path>
    ): DirectoryStream<Path> = route(directory) { it.newDirectoryStream(directory, filter) }

    final override fun createDirectory(directory: Path, vararg attributes: FileAttribute<*>) {
        route(directory) { it.createDirectory(directory, *attributes) }
    }

    final override fun createSymbolicLink(
        link: Path,
        target: Path,
        vararg attributes: FileAttribute<*>
    ) {
        route(link, target) { it.createSymbolicLink(link, target, *attributes) }
    }

    final override fun createLink(link: Path, existing: Path) {
        route(link, existing) { it.createLink(link, existing) }
    }

    final override fun delete(path: Path) {
        route(path) { it.delete(path) }
    }

    final override fun readSymbolicLink(link: Path): Path =
        route(link) { it.readSymbolicLink(link) }

    final override fun copy(source: Path, target: Path, vararg options: CopyOption) {
        route(source, target) { it.copy(source, target, *options) }
    }

    final override fun move(source: Path, target: Path, vararg options: CopyOption) {
        route(source, target) { it.move(source, target, *options) }
    }

    final override fun isSameFile(path: Path, path2: Path): Boolean =
        route(path, path2, attributeAccess = true) { it.isSameFile(path, path2) }

    final override fun isHidden(path: Path): Boolean =
        route(path, attributeAccess = true) { it.isHidden(path) }

    final override fun getFileStore(path: Path): FileStore = localProvider.getFileStore(path)

    final override fun checkAccess(path: Path, vararg modes: AccessMode) {
        route(path) { it.checkAccess(path, *modes) }
    }

    final override fun <V : FileAttributeView> getFileAttributeView(
        path: Path,
        type: Class<V>,
        vararg options: LinkOption
    ): V? = localProvider.getFileAttributeView(path, type, *options)

    final override fun <A : BasicFileAttributes> readAttributes(
        path: Path,
        type: Class<A>,
        vararg options: LinkOption
    ): A = route(path, attributeAccess = true) { it.readAttributes(path, type, *options) }

    final override fun readAttributes(
        path: Path,
        attributes: String,
        vararg options: LinkOption
    ): Map<String, Any> = route(path, attributeAccess = true) {
        it.readAttributes(path, attributes, *options)
    }

    final override fun setAttribute(
        path: Path,
        attribute: String,
        value: Any,
        vararg options: LinkOption
    ) {
        route(path, attributeAccess = true) { it.setAttribute(path, attribute, value, *options) }
    }

    final override fun observe(path: Path, intervalMillis: Long): PathObservable {
        if (localProvider !is PathObservableProvider) {
            throw UnsupportedOperationException("Local provider does not support path observation")
        }
        return route(path) { selected ->
            if (selected === localProvider) verifyObservableAccess(selected, path)
            val observable = selected as? PathObservableProvider
                ?: throw UnsupportedOperationException("Selected provider cannot observe paths")
            observable.observe(path, intervalMillis)
        }
    }

    final override fun search(
        directory: Path,
        query: String,
        intervalMillis: Long,
        listener: (List<Path>) -> Unit
    ) {
        route(directory) { selected ->
            val searchable = selected as? Searchable
                ?: throw UnsupportedOperationException("Selected provider cannot search")
            searchable.search(directory, query, intervalMillis, listener)
        }
    }

    private fun verifyObservableAccess(provider: FileSystemProvider, path: Path) {
        val attributes = try {
            provider.readAttributes(path, BasicFileAttributes::class.java)
        } catch (_: IOException) {
            provider.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        }
        if (attributes.isSymbolicLink) provider.readSymbolicLink(path)
        else provider.checkAccess(path, AccessMode.READ)
    }

    private fun <R> route(
        path: Path,
        attributeAccess: Boolean = false,
        action: (FileSystemProvider) -> R
    ): R = callRootable(path, attributeAccess, localProvider, rootProvider) { action(this) }

    private fun <R> route(
        first: Path,
        second: Path,
        attributeAccess: Boolean = false,
        action: (FileSystemProvider) -> R
    ): R = callRootable(first, second, attributeAccess, localProvider, rootProvider) { action(this) }
}
