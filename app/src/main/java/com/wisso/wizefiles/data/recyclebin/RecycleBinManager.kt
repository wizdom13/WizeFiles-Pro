// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.recyclebin

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import com.wisso.wizefiles.core.fastops.FastFileOps
import com.wisso.wizefiles.provider.common.createDirectories
import com.wisso.wizefiles.provider.common.deleteIfExists
import com.wisso.wizefiles.provider.common.exists
import com.wisso.wizefiles.provider.common.moveTo
import java.io.IOException
import java.util.Comparator
import java.util.Locale
import java.util.Properties

object RecycleBinManager {
    private const val ROOT_DIRECTORY_NAME = ".wizefiles_trash_bin"
    private const val LEGACY_ROOT_DIRECTORY_NAME = ".wizefiles_recycle_bin"
    private const val METADATA_DIRECTORY_NAME = ".metadata"

    val recycleBinRootPath: Path
        get() {
            val externalStoragePath = System.getProperty("EXTERNAL_STORAGE") ?: "/storage/emulated/0"
            return Paths.get(externalStoragePath).resolve(ROOT_DIRECTORY_NAME)
        }

    internal val legacyRecycleBinRootPath: Path
        get() {
            val externalStoragePath = System.getProperty("EXTERNAL_STORAGE") ?: "/storage/emulated/0"
            return Paths.get(externalStoragePath).resolve(LEGACY_ROOT_DIRECTORY_NAME)
        }

    fun isRecycleBinPath(path: Path, recycleBinRoot: Path = recycleBinRootPath): Boolean {
        val normalizedPath = path.normalize()
        val normalizedRoot = recycleBinRoot.normalize()
        if (normalizedPath.startsWith(normalizedRoot)) {
            return true
        }
        return normalizedRoot == recycleBinRootPath.normalize() &&
            normalizedPath.startsWith(legacyRecycleBinRootPath.normalize())
    }

    fun isRecycleBinRootPath(path: Path): Boolean {
        val normalizedPath = path.normalize()
        return normalizedPath == recycleBinRootPath.normalize() ||
            normalizedPath == legacyRecycleBinRootPath.normalize()
    }

    @Synchronized
    @Throws(IOException::class)
    fun ensureRecycleBinExists(recycleBinRoot: Path = recycleBinRootPath) {
        if (recycleBinRoot.normalize() == recycleBinRootPath.normalize()) {
            migrateLegacyRecycleBin(legacyRecycleBinRootPath, recycleBinRoot)
        }
        ensureDirectories(recycleBinRoot)
    }

    @Throws(IOException::class)
    fun hasRecycleBinContents(recycleBinRoot: Path = recycleBinRootPath): Boolean {
        ensureRecycleBinExists(recycleBinRoot)
        return Files.newDirectoryStream(recycleBinRoot).use { stream ->
            stream.any { it.fileName.toString() != METADATA_DIRECTORY_NAME }
        }
    }

    @Throws(IOException::class)
    fun clearRecycleBin(recycleBinRoot: Path = recycleBinRootPath): RecycleBinOperationSummary =
        deleteAllPermanently(recycleBinRoot)

    @Throws(IOException::class)
    fun moveToRecycleBin(path: Path, recycleBinRoot: Path = recycleBinRootPath) {
        ensureRecycleBinExists(recycleBinRoot)
        val payloadPath = createUniquePayloadPath(recycleBinRoot, path.fileName.toString())
        if (!FastFileOps.moveLocalTree(path, payloadPath)) {
            path.moveTo(payloadPath)
        }
        writeEntryMetadata(
            recycleBinRoot,
            payloadPath,
            RecycleBinMetadata(
                originalPath = path.toString(),
                deletedAtMillis = System.currentTimeMillis(),
                isDirectory = Files.isDirectory(payloadPath),
                displayName = path.fileName.toString(),
                mimeTypeHint = guessMimeTypeHint(path)
            )
        )
    }

    @Throws(IOException::class)
    fun restore(pathInRecycleBin: Path, recycleBinRoot: Path = recycleBinRootPath) {
        ensureRecycleBinExists(recycleBinRoot)
        val metadata = readEntryMetadata(pathInRecycleBin, recycleBinRoot)
        val targetPath = Paths.get(metadata.originalPath)
        targetPath.parent?.createDirectories()
        if (targetPath.exists()) {
            if (metadata.isDirectory && Files.isDirectory(pathInRecycleBin) && Files.isDirectory(targetPath)) {
                mergeDirectoryInto(pathInRecycleBin, targetPath)
                deleteEntryMetadata(pathInRecycleBin, recycleBinRoot)
                return
            }
            throw IOException("Target already exists: $targetPath")
        }
        movePath(pathInRecycleBin, targetPath)
        deleteEntryMetadata(pathInRecycleBin, recycleBinRoot)
    }

    @Throws(IOException::class)
    fun restorePath(pathInRecycleBin: Path, recycleBinRoot: Path = recycleBinRootPath) {
        ensureRecycleBinExists(recycleBinRoot)
        val entry = findEntry(pathInRecycleBin, recycleBinRoot)
        if (pathInRecycleBin == entry.path) {
            restore(entry.path, recycleBinRoot)
            return
        }
        val originalPath = resolveOriginalPath(entry, pathInRecycleBin)
            ?: throw IOException("Unable to resolve original path for $pathInRecycleBin")
        val targetPath = Paths.get(originalPath)
        targetPath.parent?.createDirectories()
        if (targetPath.exists()) {
            throw IOException("Target already exists: $targetPath")
        }
        movePath(pathInRecycleBin, targetPath)
        pruneEmptyRecycleBinDirectories(pathInRecycleBin.parent, entry.path, recycleBinRoot)
    }

    @Throws(IOException::class)
    fun restorePaths(
        pathsInRecycleBin: Iterable<Path>,
        recycleBinRoot: Path = recycleBinRootPath
    ): RecycleBinOperationSummary {
        val failures = mutableListOf<String>()
        var successCount = 0
        for (path in pathsInRecycleBin) {
            try {
                restorePath(path, recycleBinRoot)
                successCount++
            } catch (e: IOException) {
                failures += "${path.fileName}: ${e.message}"
            }
        }
        return RecycleBinOperationSummary(successCount, failures)
    }

    @Throws(IOException::class)
    fun restoreAll(recycleBinRoot: Path = recycleBinRootPath): RecycleBinOperationSummary {
        val failures = mutableListOf<String>()
        var successCount = 0
        for (entry in listEntries(recycleBinRoot)) {
            try {
                restore(entry.path, recycleBinRoot)
                successCount++
            } catch (e: IOException) {
                failures += "${entry.path.fileName}: ${e.message}"
            }
        }
        return RecycleBinOperationSummary(successCount, failures)
    }

    @Throws(IOException::class)
    fun deleteAllPermanently(recycleBinRoot: Path = recycleBinRootPath): RecycleBinOperationSummary {
        val failures = mutableListOf<String>()
        var successCount = 0
        for (entry in listEntries(recycleBinRoot)) {
            try {
                if (FastFileOps.deleteLocalTree(entry.path, false) == null) {
                    deleteRecursively(entry.path)
                }
                deleteEntryMetadata(entry.path, recycleBinRoot)
                successCount++
            } catch (e: IOException) {
                failures += "${entry.path.fileName}: ${e.message}"
            }
        }
        return RecycleBinOperationSummary(successCount, failures)
    }

    fun listEntries(recycleBinRoot: Path = recycleBinRootPath): List<RecycleBinEntry> {
        ensureRecycleBinExists(recycleBinRoot)
        return Files.newDirectoryStream(recycleBinRoot).use { stream ->
            stream.filter { it.fileName.toString() != METADATA_DIRECTORY_NAME }
                .mapNotNull { path ->
                    val metadata = runCatching { readEntryMetadata(path, recycleBinRoot) }.getOrNull()
                        ?: return@mapNotNull null
                    RecycleBinEntry(path, metadata)
                }.toList()
        }
    }

    fun getOriginalPath(pathInRecycleBin: Path, recycleBinRoot: Path = recycleBinRootPath): String? =
        runCatching { readEntryMetadata(pathInRecycleBin, recycleBinRoot).originalPath }.getOrNull()

    fun resolveOriginalPath(entry: RecycleBinEntry, pathInRecycleBin: Path): String? {
        if (!pathInRecycleBin.normalize().startsWith(entry.path.normalize())) {
            return null
        }
        if (!entry.metadata.isDirectory || pathInRecycleBin == entry.path) {
            return entry.metadata.originalPath
        }
        val originalRoot = runCatching { Paths.get(entry.metadata.originalPath) }.getOrNull() ?: return null
        val relativePath = runCatching { entry.path.relativize(pathInRecycleBin) }.getOrNull() ?: return null
        return runCatching { originalRoot.resolve(relativePath).toString() }.getOrNull()
    }

    fun resolveDisplayName(entry: RecycleBinEntry, pathInRecycleBin: Path): String? {
        if (!pathInRecycleBin.normalize().startsWith(entry.path.normalize())) {
            return null
        }
        if (!entry.metadata.isDirectory || pathInRecycleBin == entry.path) {
            return entry.metadata.displayName
        }
        return resolveOriginalPath(entry, pathInRecycleBin)?.let { originalPath ->
            runCatching { Paths.get(originalPath).fileName?.toString() }.getOrNull()
        } ?: pathInRecycleBin.fileName?.toString()
    }

    fun resolveMimeTypeHint(entry: RecycleBinEntry, pathInRecycleBin: Path): String? {
        if (!pathInRecycleBin.normalize().startsWith(entry.path.normalize())) {
            return null
        }
        val originalPath = resolveOriginalPath(entry, pathInRecycleBin)
        val displayName = resolveDisplayName(entry, pathInRecycleBin)
        return entry.metadata.mimeTypeHint
            ?.takeIf { it.isNotBlank() }
            ?: sequenceOf(originalPath, displayName, pathInRecycleBin.fileName?.toString())
                .mapNotNull(::guessMimeTypeHint)
                .firstOrNull()
    }

    @Throws(IOException::class)
    internal fun migrateLegacyRecycleBin(legacyRoot: Path, newRoot: Path) {
        val normalizedLegacyRoot = legacyRoot.normalize()
        val normalizedNewRoot = newRoot.normalize()
        if (normalizedLegacyRoot == normalizedNewRoot || !legacyRoot.exists()) {
            return
        }
        if (!newRoot.exists()) {
            newRoot.parent?.createDirectories()
            movePath(legacyRoot, newRoot)
            return
        }

        ensureDirectories(legacyRoot)
        ensureDirectories(newRoot)
        for (entry in listEntries(legacyRoot)) {
            val destination = createUniquePayloadPath(newRoot, entry.path.fileName.toString())
            movePath(entry.path, destination)
            try {
                writeEntryMetadata(newRoot, destination, entry.metadata)
            } catch (exception: IOException) {
                runCatching { movePath(destination, entry.path) }
                    .onFailure(exception::addSuppressed)
                throw exception
            }
            deleteEntryMetadata(entry.path, legacyRoot)
        }

        val legacyMetadataDirectory = metadataDirectory(legacyRoot)
        if (legacyMetadataDirectory.exists() && isDirectoryEmpty(legacyMetadataDirectory)) {
            legacyMetadataDirectory.deleteIfExists()
        }
        if (legacyRoot.exists() && isDirectoryEmpty(legacyRoot)) {
            legacyRoot.deleteIfExists()
        }
    }

    private fun ensureDirectories(recycleBinRoot: Path) {
        recycleBinRoot.createDirectories()
        metadataDirectory(recycleBinRoot).createDirectories()
    }

    private fun findEntry(pathInRecycleBin: Path, recycleBinRoot: Path): RecycleBinEntry {
        val normalizedPath = pathInRecycleBin.normalize()
        return listEntries(recycleBinRoot).firstOrNull { normalizedPath.startsWith(it.path.normalize()) }
            ?: throw IOException("No recycle bin entry found for $pathInRecycleBin")
    }

    private fun mergeDirectoryInto(sourceDirectory: Path, targetDirectory: Path) {
        Files.newDirectoryStream(sourceDirectory).use { stream ->
            for (child in stream) {
                val targetChild = targetDirectory.resolve(child.fileName.toString())
                if (Files.isDirectory(child)) {
                    when {
                        targetChild.exists() && Files.isDirectory(targetChild) -> mergeDirectoryInto(child, targetChild)
                        targetChild.exists() -> throw IOException("Target already exists: $targetChild")
                        else -> movePath(child, targetChild)
                    }
                } else {
                    if (targetChild.exists()) {
                        throw IOException("Target already exists: $targetChild")
                    }
                    movePath(child, targetChild)
                }
            }
        }
        if (isDirectoryEmpty(sourceDirectory)) {
            sourceDirectory.deleteIfExists()
        }
    }

    private fun pruneEmptyRecycleBinDirectories(path: Path?, entryRoot: Path, recycleBinRoot: Path) {
        var current = path
        while (current != null && current.normalize().startsWith(entryRoot.normalize())) {
            if (!Files.isDirectory(current) || !isDirectoryEmpty(current)) {
                break
            }
            current.deleteIfExists()
            if (current == entryRoot) {
                deleteEntryMetadata(entryRoot, recycleBinRoot)
                break
            }
            current = current.parent
        }
    }

    private fun isDirectoryEmpty(path: Path): Boolean =
        Files.newDirectoryStream(path).use { !it.iterator().hasNext() }

    private fun movePath(source: Path, target: Path) {
        target.parent?.createDirectories()
        if (!FastFileOps.moveLocalTree(source, target)) {
            source.moveTo(target)
        }
    }

    private fun createUniquePayloadPath(recycleBinRoot: Path, name: String): Path {
        val base = name.ifEmpty { "item" }
        var candidate = recycleBinRoot.resolve(base)
        var index = 2
        while (candidate.exists()) {
            candidate = recycleBinRoot.resolve("$base ($index)")
            index++
        }
        return candidate
    }

    private fun metadataDirectory(recycleBinRoot: Path): Path =
        recycleBinRoot.resolve(METADATA_DIRECTORY_NAME)

    private fun metadataPath(pathInRecycleBin: Path, recycleBinRoot: Path): Path =
        metadataDirectory(recycleBinRoot).resolve("${pathInRecycleBin.fileName}.properties")

    private fun writeEntryMetadata(
        recycleBinRoot: Path,
        pathInRecycleBin: Path,
        metadata: RecycleBinMetadata
    ) {
        val props = Properties().apply {
            setProperty("originalPath", metadata.originalPath)
            setProperty("deletedAtMillis", metadata.deletedAtMillis.toString())
            setProperty("isDirectory", metadata.isDirectory.toString())
            setProperty("displayName", metadata.displayName)
            metadata.mimeTypeHint?.takeIf { it.isNotBlank() }?.let { setProperty("mimeTypeHint", it) }
        }
        Files.newOutputStream(metadataPath(pathInRecycleBin, recycleBinRoot)).use {
            props.store(it, null)
        }
    }

    private fun readEntryMetadata(pathInRecycleBin: Path, recycleBinRoot: Path): RecycleBinMetadata {
        val properties = Properties()
        Files.newInputStream(metadataPath(pathInRecycleBin, recycleBinRoot)).use {
            properties.load(it)
        }
        return RecycleBinMetadata(
            originalPath = properties.getProperty("originalPath"),
            deletedAtMillis = properties.getProperty("deletedAtMillis").toLong(),
            isDirectory = properties.getProperty("isDirectory").toBoolean(),
            displayName = properties.getProperty("displayName"),
            mimeTypeHint = properties.getProperty("mimeTypeHint")
        )
    }

    private fun deleteEntryMetadata(pathInRecycleBin: Path, recycleBinRoot: Path) {
        metadataPath(pathInRecycleBin, recycleBinRoot).deleteIfExists()
    }

    private fun deleteRecursively(path: Path) {
        Files.walk(path)
            .sorted(Comparator.reverseOrder())
            .forEach { Files.deleteIfExists(it) }
    }
}

data class RecycleBinEntry(val path: Path, val metadata: RecycleBinMetadata)

data class RecycleBinMetadata(
    val originalPath: String,
    val deletedAtMillis: Long,
    val isDirectory: Boolean,
    val displayName: String,
    val mimeTypeHint: String? = null
)

private fun guessMimeTypeHint(candidate: Path): String? = guessMimeTypeHint(candidate.toString())

private fun guessMimeTypeHint(candidate: String?): String? {
    val extension = candidate
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase(Locale.ROOT)
        ?.takeIf { it.isNotBlank() }
        ?: return null
    return when (extension) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "heic" -> "image/heic"
        "heif" -> "image/heif"
        "avif" -> "image/avif"
        "mp4" -> "video/mp4"
        "3gp" -> "video/3gpp"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        "avi" -> "video/x-msvideo"
        "m4v" -> "video/mp4"
        else -> null
    }
}

data class RecycleBinOperationSummary(val successCount: Int, val failures: List<String>) {
    val hasFailures: Boolean
        get() = failures.isNotEmpty()
}
