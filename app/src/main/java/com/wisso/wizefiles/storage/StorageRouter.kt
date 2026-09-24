package com.wisso.wizefiles.storage

import java.nio.file.CopyOption
import java.nio.file.Path
import com.wisso.wizefiles.provider.os.isLinuxPath
import com.wisso.wizefiles.storage.local.LocalFileNode
import com.wisso.wizefiles.storage.local.LocalStorageBackend
import java.io.IOException

/**
 * Small coordination layer for incremental retrofile replacement.
 *
 * It routes local filesystem requests to [LocalStorageBackend], while non-local requests
 * remain on existing retrofile-based flows until migrated.
 */
class StorageRouter(
    private val localStorageBackend: LocalStorageBackend = LocalStorageBackend()
) {
    @Throws(IOException::class)
    fun exists(path: Path): Boolean? {
        if (!path.isLocalPath()) {
            return null
        }
        return localStorageBackend.exists(LocalFileNode(path.toFile()))
    }

    @Throws(IOException::class)
    fun list(path: Path): List<LocalFileNode>? {
        if (!path.isLocalPath()) {
            return null
        }
        return localStorageBackend.list(LocalFileNode(path.toFile()))
    }

    @Throws(IOException::class)
    fun stat(node: LocalFileNode): FileMetadata? = localStorageBackend.stat(node)

    @Throws(IOException::class)
    fun createDirectory(path: Path): LocalFileNode? {
        if (!path.isLocalPath()) {
            return null
        }
        return localStorageBackend.createDirectory(LocalFileNode(path.toFile()))
    }

    @Throws(IOException::class)
    fun delete(path: Path): Boolean {
        if (!path.isLocalPath()) {
            return false
        }
        localStorageBackend.delete(LocalFileNode(path.toFile()))
        return true
    }

    @Throws(IOException::class)
    fun rename(path: Path, newName: String): LocalFileNode? {
        if (!path.isLocalPath()) {
            return null
        }
        return localStorageBackend.rename(LocalFileNode(path.toFile()), newName)
    }

    @Throws(IOException::class)
    fun copy(source: Path, target: Path, vararg options: CopyOption): Boolean {
        val sourceFile = source.toLocalFileOrNull() ?: return false
        val targetFile = target.toLocalFileOrNull() ?: return false
        localStorageBackend.copy(LocalFileNode(sourceFile), LocalFileNode(targetFile), *options)
        return true
    }

    @Throws(IOException::class)
    fun move(source: Path, target: Path, vararg options: CopyOption): Boolean {
        val sourceFile = source.toLocalFileOrNull() ?: return false
        val targetFile = target.toLocalFileOrNull() ?: return false
        return localStorageBackend.move(LocalFileNode(sourceFile), LocalFileNode(targetFile), *options)
    }
}

private fun Path.isLocalPath(): Boolean =
    isLinuxPath || toLocalFileOrNull() != null

private fun Path.toLocalFileOrNull() = runCatching { toFile() }.getOrNull()
