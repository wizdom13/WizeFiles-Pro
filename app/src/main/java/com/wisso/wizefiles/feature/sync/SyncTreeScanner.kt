// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.storage.path.toAppPathOrNull
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.storage.path.toUriString
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

internal data class SyncScanResult(
    val rootUri: String,
    val storageIdentity: String,
    val entries: List<SyncFileEntry>,
    val errors: List<String>
) {
    val complete: Boolean
        get() = errors.isEmpty()
}

internal object SyncPathResolver {
    fun resolve(uri: String): Path? = uri.toAppPathOrNull()?.toLegacyPathOrNull()

    fun childUri(rootUri: String, relativePath: String): String {
        val root = resolve(rootUri) ?: return rootUri.trimEnd('/') + "/" + relativePath
        val child = relativePath.replace('\\', '/').split('/')
            .filter(String::isNotEmpty)
            .fold(root) { path, segment -> path.resolve(segment) }
        return child.toAppPath().toUriString()
    }
}

internal class SyncTreeScanner {
    private companion object {
        const val MAX_SCAN_ERROR_LENGTH = 320
    }

    fun scan(rootUri: String): SyncScanResult {
        val root = SyncPathResolver.resolve(rootUri)
            ?: return SyncScanResult(rootUri, "", emptyList(), listOf("PATH_UNAVAILABLE"))
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return SyncScanResult(rootUri, identity(rootUri, root), emptyList(), listOf("ROOT_UNAVAILABLE"))
        }
        val entries = mutableListOf<SyncFileEntry>()
        val errors = mutableListOf<String>()
        try {
            Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(
                directory: Path,
                attributes: BasicFileAttributes
            ): FileVisitResult {
                if (directory != root) entries += entry(root, directory, attributes)
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                entries += entry(root, file, attributes)
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exception: IOException): FileVisitResult {
                if (exception is InterruptedIOException) throw exception
                errors += scanFailure(root, file, exception)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(
                directory: Path,
                exception: IOException?
            ): FileVisitResult {
                if (exception is InterruptedIOException) throw exception
                if (exception != null) {
                    errors += scanFailure(root, directory, exception)
                }
                return FileVisitResult.CONTINUE
            }
            })
        } catch (exception: InterruptedIOException) {
            throw exception
        } catch (exception: Exception) {
            errors += scanFailure(root, root, exception)
        }
        return SyncScanResult(rootUri, identity(rootUri, root), entries, errors)
    }

    private fun scanFailure(root: Path, path: Path, exception: Exception): String {
        val relativePath = syncRelativePath(root, path).ifBlank { "/" }
        val detail = exception.message
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.take(MAX_SCAN_ERROR_LENGTH)
        return buildString {
            append(relativePath).append(": ").append(exception.javaClass.simpleName)
            if (detail != null && detail != relativePath) {
                append(" — ").append(detail)
            }
        }
    }

    private fun entry(root: Path, path: Path, attributes: BasicFileAttributes) = SyncFileEntry(
        relativePath = syncRelativePath(root, path),
        uri = path.toAppPath().toUriString(),
        isDirectory = attributes.isDirectory,
        isSymlink = attributes.isSymbolicLink,
        sizeBytes = if (attributes.isDirectory) 0 else attributes.size().coerceAtLeast(0),
        modifiedAtMillis = attributes.lastModifiedTime().toMillis().coerceAtLeast(0),
        modifiedPrecisionMillis = 1_000
    )

    private fun identity(rootUri: String, root: Path): String =
        DefaultSyncProviderCapabilityResolver.storageIdentity(rootUri) ?: runCatching {
            val store = Files.getFileStore(root)
            "${store.name()}:${store.type()}:${root.root}"
        }.getOrDefault(root.toUri().normalize().toString())
}

internal fun syncRelativePath(root: Path, path: Path): String =
    root.relativize(path).toString().replace('\\', '/').trim('/')

internal fun SyncScanResult.toSnapshots(
    profileId: String,
    generation: Long,
    side: SyncSide
): List<SyncSnapshotEntry> = entries.map { entry ->
    SyncSnapshotEntry(
        profileId = profileId,
        generation = generation,
        side = side,
        relativePath = entry.normalizedRelativePath,
        isDirectory = entry.isDirectory,
        sizeBytes = entry.sizeBytes,
        modifiedAtMillis = entry.modifiedAtMillis,
        modifiedPrecisionMillis = entry.modifiedPrecisionMillis,
        revision = entry.revision,
        checksum = entry.checksum,
        providerIdentity = storageIdentity
    )
}

internal fun SyncSnapshotEntry.toFileEntry(rootUri: String): SyncFileEntry = SyncFileEntry(
    relativePath = relativePath,
    uri = SyncPathResolver.childUri(rootUri, relativePath),
    isDirectory = isDirectory,
    sizeBytes = sizeBytes,
    modifiedAtMillis = modifiedAtMillis,
    modifiedPrecisionMillis = modifiedPrecisionMillis,
    revision = revision,
    checksum = checksum
)
