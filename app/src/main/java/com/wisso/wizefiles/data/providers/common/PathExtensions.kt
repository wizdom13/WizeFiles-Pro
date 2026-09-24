// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import java.nio.channels.SeekableByteChannel
import java.nio.file.AccessMode
import java.nio.file.CopyOption
import java.nio.file.DirectoryStream
import java.nio.file.FileStore
import java.nio.file.Files
import java.nio.file.FileVisitResult
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.ProviderMismatchException
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileAttributeView
import java.nio.file.attribute.FileOwnerAttributeView
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.GroupPrincipal
import java.nio.file.attribute.UserPrincipal
import java.nio.file.spi.FileSystemProvider
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.InterruptedIOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.channels.ClosedByInterruptException
import java.nio.charset.Charset
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import com.wisso.wizefiles.provider.os.isLinuxPath
import java.nio.file.attribute.PosixFileAttributeView as Java8PosixFileAttributeView
import com.wisso.wizefiles.core.fastops.FastFileOps

@Throws(IOException::class)
fun Path.checkAccess(vararg modes: AccessMode) {
    provider.checkAccess(this, *modes)
}

// Can handle ProgressCopyOption.
@Throws(IOException::class)
fun Path.copyTo(target: Path, vararg options: CopyOption) {
    val provider = provider
    if (provider == target.provider && !shouldUseForeignCopyMoveForProgress(this, target, options)) {
        provider.copy(this, target, *options)
    } else {
        ForeignCopyMove.copy(this, target, *options)
    }
}

@Throws(IOException::class)
fun Path.createDirectory(vararg attributes: FileAttribute<*>): Path =
    Files.createDirectory(this, *attributes)

@Throws(IOException::class)
fun Path.createDirectories(vararg attributes: FileAttribute<*>): Path =
    Files.createDirectories(this, *attributes)

@Throws(IOException::class)
fun Path.createFile(vararg attributes: FileAttribute<*>): Path =
    try {
        // This uses newByteChannel() under the hood, which may not be supported.
        Files.createFile(this, *attributes)
    } catch (e: UnsupportedOperationException) {
        Files.newOutputStream(this, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).close()
        this
    }

@Throws(IOException::class)
fun Path.createSymbolicLink(target: Path, vararg attributes: FileAttribute<*>): Path =
    Files.createSymbolicLink(this, target, *attributes)

@Throws(IOException::class)
fun Path.createSymbolicLink(target: ByteString, vararg attributes: FileAttribute<*>): Path =
    createSymbolicLink(ByteStringPath(target), *attributes)

@Throws(IOException::class)
fun Path.delete() {
    Files.delete(this)
}

@Throws(IOException::class)
fun Path.deleteIfExists() {
    Files.deleteIfExists(this)
}

fun Path.exists(vararg options: LinkOption): Boolean = Files.exists(this, *options)

fun <V : FileAttributeView> Path.getFileAttributeView(
    type: Class<V>,
    vararg options: LinkOption
): V? = Files.getFileAttributeView(this, type, *options)

@Throws(IOException::class)
fun Path.getFileStore(): FileStore = Files.getFileStore(this)

@Throws(IOException::class)
fun Path.getLastModifiedTime(vararg options: LinkOption): FileTime =
    Files.getLastModifiedTime(this, *options)

@Throws(IOException::class)
fun Path.getMode(vararg options: LinkOption): Set<PosixFileModeBit>? =
    Files.readAttributes(this, PosixFileAttributes::class.java, *options).mode()

@Throws(IOException::class)
fun Path.getOwner(vararg options: LinkOption): UserPrincipal = Files.getOwner(this, *options)

@Throws(IOException::class)
fun Path.moveTo(target: Path, vararg options: CopyOption) {
    val provider = provider
    if (provider == target.provider && !shouldUseForeignCopyMoveForProgress(this, target, options)) {
        provider.move(this, target, *options)
    } else {
        ForeignCopyMove.move(this, target, *options)
    }
}

internal fun shouldUseForeignCopyMoveForProgress(
    source: Path,
    target: Path,
    options: Array<out CopyOption>
): Boolean {
    val hasProgressOption = options.any { it is ProgressCopyOption }
    return hasProgressOption && (!source.isLinuxPath || !target.isLinuxPath)
}

@Throws(IOException::class)
fun Path.newBufferedReader(charset: Charset, vararg options: OpenOption): BufferedReader =
    BufferedReader(InputStreamReader(newInputStream(*options), charset.newDecoder()))

@Throws(IOException::class)
fun Path.newBufferedWriter(charset: Charset, vararg options: OpenOption): BufferedWriter =
    BufferedWriter(OutputStreamWriter(newOutputStream(*options), charset.newEncoder()))

@Throws(IOException::class)
fun Path.newByteChannel(
    options: Set<OpenOption>,
    vararg attributes: FileAttribute<*>
): SeekableByteChannel =
    try {
        Files.newByteChannel(this, options, *attributes)
    } catch (e: UnsupportedOperationException) {
        throw IOException(e)
    }

@Throws(IOException::class)
fun Path.newByteChannel(vararg options: OpenOption): SeekableByteChannel =
    try {
        Files.newByteChannel(this, *options)
    } catch (e: UnsupportedOperationException) {
        throw IOException(e)
    }

@Throws(IOException::class)
fun Path.newDirectoryStream(): DirectoryStream<Path> = Files.newDirectoryStream(this)

@Throws(IOException::class)
fun Path.newInputStream(vararg options: OpenOption): InputStream =
    InterruptedIOExceptionInputStream(Files.newInputStream(this, *options))

private class InterruptedIOExceptionInputStream(
    inputStream: InputStream
) : ForwardingInputStream(inputStream) {
    @Throws(IOException::class)
    override fun read(): Int =
        try {
            super.read()
        } catch (e: ClosedByInterruptException) {
            throw e.toInterruptedIOException()
        }

    @Throws(IOException::class)
    override fun read(b: ByteArray): Int =
        try {
            super.read(b)
        } catch (e: ClosedByInterruptException) {
            throw e.toInterruptedIOException()
        }

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int =
        try {
            super.read(b, off, len)
        } catch (e: ClosedByInterruptException) {
            throw e.toInterruptedIOException()
        }

    @Throws(IOException::class)
    override fun skip(n: Long): Long = try {
            super.skip(n)
        } catch (e: ClosedByInterruptException) {
            throw e.toInterruptedIOException()
        }

    @Throws(IOException::class)
    override fun available(): Int =
        try {
            super.available()
        } catch (e: ClosedByInterruptException) {
            throw e.toInterruptedIOException()
        }

    @Throws(IOException::class)
    override fun close() {
        try {
            super.close()
        } catch (e: ClosedByInterruptException) {
            throw e.toInterruptedIOException()
        }
    }

    @Throws(IOException::class)
    override fun reset() {
        try {
            super.reset()
        } catch (e: ClosedByInterruptException) {
            throw e.toInterruptedIOException()
        }
    }
}

@Throws(IOException::class)
fun Path.newOutputStream(vararg options: OpenOption): OutputStream =
    InterruptedIOExceptionOutputStream(Files.newOutputStream(this, *options))

private class InterruptedIOExceptionOutputStream(
    outputStream: OutputStream
) : ForwardingOutputStream(outputStream) {
    @Throws(IOException::class)
    override fun write(b: Int) {
        try {
            super.write(b)
        } catch (e: ClosedByInterruptException) {
            throw e.toInterruptedIOException()
        }
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray) {
        try {
            super.write(b)
        } catch (e: ClosedByInterruptException) {
            throw e.toInterruptedIOException()
        }
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
        try {
            super.write(b, off, len)
        } catch (e: ClosedByInterruptException) {
            throw e.toInterruptedIOException()
        }
    }

    @Throws(IOException::class)
    override fun flush() {
        try {
            super.flush()
        } catch (e: ClosedByInterruptException) {
            throw e.toInterruptedIOException()
        }
    }

    @Throws(IOException::class)
    override fun close() {
        try {
            super.close()
        } catch (e: ClosedByInterruptException) {
            throw e.toInterruptedIOException()
        }
    }
}

private fun ClosedByInterruptException.toInterruptedIOException(): InterruptedIOException {
    Thread.interrupted()
    return InterruptedIOException().apply { initCause(this@toInterruptedIOException) }
}

@Throws(IOException::class)
fun Path.observe(intervalMillis: Long): PathObservable {
    val observableProvider = provider as? PathObservableProvider
        ?: throw UnsupportedOperationException(
            "Path provider does not support observe(): ${provider.javaClass.name}"
        )
    return observableProvider.observe(this, intervalMillis)
}

val Path.provider: FileSystemProvider
    get() = fileSystem.provider()

// TODO: Just use Files.readAllBytes(), if all our providers support
//  newByteChannel()?
// Uses newInputStream() instead of newByteChannel().
@Throws(IOException::class)
fun Path.readAllBytes(vararg options: OpenOption): ByteArray =
    newInputStream(*options).use { it.readBytes() }

@Throws(IOException::class)
fun <A : BasicFileAttributes> Path.readAttributes(type: Class<A>, vararg options: LinkOption): A =
    Files.readAttributes(this, type, *options)

fun Path.isDirectory(vararg options: LinkOption): Boolean = Files.isDirectory(this, *options)

val Path.isHidden: Boolean
    @Throws(IOException::class)
    get() = Files.isHidden(this)

val Path.isReadable: Boolean
    get() = Files.isReadable(this)

fun Path.isRegularFile(vararg options: LinkOption): Boolean = Files.isRegularFile(this, *options)

@Throws(IOException::class)
fun Path.isSameFile(path2: Path): Boolean = Files.isSameFile(this, path2)

val Path.isWritable: Boolean
    get() = Files.isWritable(this)

fun Path.readSymbolicLink(): Path = Files.readSymbolicLink(this)

fun Path.readSymbolicLinkByteString(): ByteString {
    val target = readSymbolicLink()
    target as? ByteStringPath ?: throw ProviderMismatchException(target.toString())
    return target.toByteString()
}

// Can resolve path in a foreign provider.
fun Path.resolveForeign(other: Path): Path {
    if (javaClass == other.javaClass && provider == other.provider
        && fileSystem == other.fileSystem) {
        return resolve(other)
    }
    if (other.isAbsolute) {
        return other
    }
    if (other.nameCount == 0) {
        return this
    }
    val thisByteStringPath = this as? ByteStringListPath<*>
    val otherByteStringPath = other as? ByteStringListPath<*>
    if (thisByteStringPath != null && otherByteStringPath != null) {
        var result: ByteStringListPath<*> = thisByteStringPath
        for (name in otherByteStringPath.nameByteStrings) {
            result = result.resolve(name)
        }
        return result
    }
    return try {
        resolve(other)
    } catch (e: ProviderMismatchException) {
        resolve(other.toString())
    } catch (e: RuntimeException) {
        resolve(other.toString())
    }
}

@Throws(IOException::class)
fun Path.search(query: String, intervalMillis: Long, listener: (List<Path>) -> Unit) {
    val searchableProvider = provider as? Searchable
    if (searchableProvider != null) {
        searchableProvider.search(this, query, intervalMillis, listener)
        return
    }
    if (isLinuxPath || provider.scheme.equals("file", ignoreCase = true)) {
        searchLocalFallback(query, intervalMillis, listener)
    } else {
        listener(emptyList())
    }
}

@Throws(IOException::class)
private fun Path.searchLocalFallback(
    query: String,
    intervalMillis: Long,
    listener: (List<Path>) -> Unit
) {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) {
        listener(emptyList())
        return
    }
    if (!Files.exists(this)) {
        listener(emptyList())
        return
    }
    if (FastFileOps.searchLocalTree(this, normalizedQuery, intervalMillis, listener)) {
        return
    }
    val batchResults = mutableListOf<Path>()
    var lastDispatchUptimeMillis = System.currentTimeMillis()
    Files.walkFileTree(this, object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(directory: Path, attributes: BasicFileAttributes): FileVisitResult {
            if (directory != this@searchLocalFallback && directory.fileName?.toString()
                    ?.contains(normalizedQuery, ignoreCase = true) == true) {
                batchResults.add(directory)
            }
            val now = System.currentTimeMillis()
            if (now - lastDispatchUptimeMillis >= intervalMillis && batchResults.isNotEmpty()) {
                listener(batchResults.toList())
                batchResults.clear()
                lastDispatchUptimeMillis = now
            }
            return FileVisitResult.CONTINUE
        }

        override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
            if (file.fileName?.toString()?.contains(normalizedQuery, ignoreCase = true) == true) {
                batchResults.add(file)
            }
            val now = System.currentTimeMillis()
            if (now - lastDispatchUptimeMillis >= intervalMillis && batchResults.isNotEmpty()) {
                listener(batchResults.toList())
                batchResults.clear()
                lastDispatchUptimeMillis = now
            }
            return FileVisitResult.CONTINUE
        }

        override fun visitFileFailed(file: Path, exception: IOException): FileVisitResult {
            return FileVisitResult.CONTINUE
        }
    })
    if (batchResults.isNotEmpty()) {
        listener(batchResults.toList())
    }
}

@Throws(IOException::class)
fun Path.setGroup(group: GroupPrincipal, vararg options: LinkOption) {
    val view = getFileAttributeView(Java8PosixFileAttributeView::class.java, *options)
        ?: throw UnsupportedOperationException()
    view.setGroup(group)
}

@Throws(IOException::class)
fun Path.setLastModifiedTime(time: FileTime) {
    Files.setLastModifiedTime(this, time)
}

@Throws(IOException::class)
fun Path.setMode(mode: Set<PosixFileModeBit>) {
    val view = Files.getFileAttributeView(this, PosixFileAttributeView::class.java)
        ?: throw UnsupportedOperationException()
    view.setMode(mode)
}

@Throws(IOException::class)
fun Path.setOwner(owner: UserPrincipal, vararg options: LinkOption) {
    val view = getFileAttributeView(FileOwnerAttributeView::class.java, *options)
        ?: throw UnsupportedOperationException()
    view.owner = owner
}

@Throws(IOException::class)
fun Path.setSeLinuxContext(seLinuxContext: ByteString, vararg options: LinkOption) {
    val view = getFileAttributeView(PosixFileAttributeView::class.java, *options)
        ?: throw UnsupportedOperationException()
    view.setSeLinuxContext(seLinuxContext)
}

// Can accept link options.
@Throws(IOException::class)
fun Path.size(vararg options: LinkOption): Long =
    readAttributes(BasicFileAttributes::class.java, *options).size()

@Throws(IOException::class)
fun Path.restoreSeLinuxContext(vararg options: LinkOption) {
    val view = getFileAttributeView(PosixFileAttributeView::class.java, *options)
        ?: throw UnsupportedOperationException()
    view.restoreSeLinuxContext()
}

@OptIn(ExperimentalContracts::class)
fun Path.asByteStringListPath(): ByteStringListPath<*> {
    contract {
        returns() implies (this@asByteStringListPath is ByteStringListPath<*>)
    }
    this as? ByteStringListPath<*> ?: throw ProviderMismatchException(toString())
    return this
}
