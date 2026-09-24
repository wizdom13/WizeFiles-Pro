// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage.archive

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/**
 * App-owned traversal helper for archive-related tree walking during retrofile migration.
 *
 * This keeps traversal mechanics out of feature jobs while preserving current provider behavior.
 */
object ArchiveTreeTraverser {
    @Throws(IOException::class)
    fun walkPreOrder(
        source: Path,
        onDirectory: (Path) -> Unit,
        onFile: (Path) -> Unit
    ) {
        walk(source, onDirectory, onFile)
    }

    @Throws(IOException::class)
    fun walkPreOrderWithPrune(
        source: Path,
        onDirectory: (Path) -> Boolean,
        onFile: (Path) -> Unit
    ) {
        walkWithPrune(source, onDirectory, onFile)
    }

    @Throws(IOException::class)
    private fun walk(
        path: Path,
        onDirectory: (Path) -> Unit,
        onFile: (Path) -> Unit
    ) {
        val attributes = Files.readAttributes(path, BasicFileAttributes::class.java)
        if (attributes.isDirectory) {
            onDirectory(path)
            Files.newDirectoryStream(path).use { directoryStream ->
                for (child in directoryStream) {
                    walk(child, onDirectory, onFile)
                }
            }
            return
        }
        onFile(path)
    }

    @Throws(IOException::class)
    private fun walkWithPrune(
        path: Path,
        onDirectory: (Path) -> Boolean,
        onFile: (Path) -> Unit
    ) {
        val attributes = Files.readAttributes(path, BasicFileAttributes::class.java)
        if (attributes.isDirectory) {
            if (!onDirectory(path)) {
                return
            }
            Files.newDirectoryStream(path).use { directoryStream ->
                for (child in directoryStream) {
                    walkWithPrune(child, onDirectory, onFile)
                }
            }
            return
        }
        onFile(path)
    }
}
