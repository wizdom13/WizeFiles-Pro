// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage.local

import com.wisso.wizefiles.storage.FileMetadata
import com.wisso.wizefiles.storage.StorageBackend
import java.io.File
import java.io.IOException
import java.nio.file.CopyOption
import com.wisso.wizefiles.provider.common.copyTo
import com.wisso.wizefiles.provider.common.moveTo

/**
 * First concrete backend in the storage replacement foundation.
 *
 * Uses java.io.File directly so existing retrofile-based features can be migrated incrementally
 * without changing call sites in this step.
 */
class LocalStorageBackend : StorageBackend<LocalFileNode> {
    override val backendId: String = BACKEND_ID

    @Throws(IOException::class)
    override fun exists(node: LocalFileNode): Boolean = node.file.exists()

    @Throws(IOException::class)
    override fun stat(node: LocalFileNode): FileMetadata? {
        val file = node.file
        if (!file.exists()) {
            return null
        }
        return file.toMetadata()
    }

    @Throws(IOException::class)
    override fun list(node: LocalFileNode): List<LocalFileNode> {
        val children = node.file.listFiles() ?: return emptyList()
        return children.map(::LocalFileNode)
    }

    @Throws(IOException::class)
    fun createDirectory(node: LocalFileNode): LocalFileNode {
        val file = node.file
        if (file.mkdir()) {
            return node
        }
        throw IOException("Failed to create directory: ${file.absolutePath}")
    }

    @Throws(IOException::class)
    fun delete(node: LocalFileNode) {
        val file = node.file
        if (file.delete()) {
            return
        }
        throw IOException("Failed to delete path: ${file.absolutePath}")
    }

    @Throws(IOException::class)
    fun rename(node: LocalFileNode, newName: String): LocalFileNode {
        if (newName.contains(File.separatorChar) || newName.contains('/')) {
            throw IOException("Rename only supports same-parent target names")
        }
        val source = node.file
        val target = File(source.parentFile, newName)
        if (source.renameTo(target)) {
            return LocalFileNode(target)
        }
        throw IOException("Failed to rename path: ${source.absolutePath}")
    }

    @Throws(IOException::class)
    fun copy(source: LocalFileNode, target: LocalFileNode, vararg options: CopyOption): LocalFileNode {
        source.file.toPath().copyTo(target.file.toPath(), *options)
        return target
    }

    @Throws(IOException::class)
    fun move(source: LocalFileNode, target: LocalFileNode, vararg options: CopyOption): Boolean {
        source.file.toPath().moveTo(target.file.toPath(), *options)
        return true
    }


    private fun File.toMetadata() = FileMetadata(
        isDirectory = isDirectory,
        sizeBytes = if (isDirectory) null else length(),
        lastModifiedEpochMillis = lastModified().takeIf { it > 0L }
    )

    companion object {
        const val BACKEND_ID = "local"
    }
}
