// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage.local

import java.io.File

/**
 * App-owned local tree traversal helper for incremental migration away from retrofile walk APIs.
 *
 * Traversal is post-order so children are visited before parent, which matches recursive delete.
 */
object LocalTreeTraverser {
    fun walkPostOrder(root: LocalFileNode): List<LocalFileNode> {
        val visited = mutableListOf<LocalFileNode>()
        walkPostOrder(root.file, visited)
        return visited
    }

    private fun walkPostOrder(file: File, visited: MutableList<LocalFileNode>) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                walkPostOrder(child, visited)
            }
        }
        visited += LocalFileNode(file)
    }

    fun walkPreOrder(
        root: LocalFileNode,
        onDirectory: (LocalFileNode) -> Boolean,
        onFile: (LocalFileNode) -> Unit
    ) {
        walkPreOrder(root.file, onDirectory, onFile)
    }

    private fun walkPreOrder(
        file: File,
        onDirectory: (LocalFileNode) -> Boolean,
        onFile: (LocalFileNode) -> Unit
    ) {
        val node = LocalFileNode(file)
        if (file.isDirectory) {
            if (!onDirectory(node)) {
                return
            }
            file.listFiles()?.forEach { child ->
                walkPreOrder(child, onDirectory, onFile)
            }
        } else {
            onFile(node)
        }
    }

    fun walkPreOrderWithPost(
        root: LocalFileNode,
        onDirectory: (LocalFileNode) -> Boolean,
        onFile: (LocalFileNode) -> Unit,
        onDirectoryPost: (LocalFileNode) -> Unit
    ) {
        walkPreOrderWithPost(root.file, onDirectory, onFile, onDirectoryPost)
    }

    private fun walkPreOrderWithPost(
        file: File,
        onDirectory: (LocalFileNode) -> Boolean,
        onFile: (LocalFileNode) -> Unit,
        onDirectoryPost: (LocalFileNode) -> Unit
    ) {
        val node = LocalFileNode(file)
        if (file.isDirectory) {
            if (!onDirectory(node)) {
                return
            }
            file.listFiles()?.forEach { child ->
                walkPreOrderWithPost(child, onDirectory, onFile, onDirectoryPost)
            }
            onDirectoryPost(node)
        } else {
            onFile(node)
        }
    }
}
