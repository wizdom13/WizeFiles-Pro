// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import java.nio.file.Path
import java.nio.file.Paths

/** Absolute device path, or a path relative to a configured storage's stable ID. */
internal data class BrowserTabLocation(val path: String, val storageId: Long? = null) {
    fun resolve(roots: List<Pair<Long, Path>>): Path? = runCatching {
        if (storageId == null) {
            Paths.get(path).takeIf { it.isAbsolute }
        } else {
            val root = roots.firstOrNull { it.first == storageId }?.second ?: return null
            root.resolve(path).normalize().takeIf { it.startsWith(root) }
        }
    }.getOrNull()

    companion object {
        fun capture(
            path: Path,
            isDevicePath: Boolean,
            roots: List<Pair<Long, Path>>
        ): BrowserTabLocation? {
            if (isDevicePath) {
                return path.takeIf { it.isAbsolute }?.let { BrowserTabLocation(it.toString()) }
            }
            return roots.sortedByDescending { it.second.nameCount }.firstNotNullOfOrNull { (id, root) ->
                runCatching {
                    if (path.startsWith(root)) BrowserTabLocation(root.relativize(path).toString(), id)
                    else null
                }.getOrNull()
            }
        }
    }
}
