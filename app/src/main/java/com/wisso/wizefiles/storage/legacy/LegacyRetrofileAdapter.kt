// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage.legacy

import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.core.files.model.loadFileItem
import com.wisso.wizefiles.storage.path.AppPath
import java.io.IOException
import java.nio.file.CopyOption

/**
 * Transitional boundary for retrofile-based operations that are not migrated yet.
 *
 * This keeps remaining legacy calls out of feature/UI classes while migration to app-owned storage
 * proceeds backend by backend.
 */
class LegacyRetrofileAdapter {
    @Throws(IOException::class)
    fun listFileItems(path: AppPath): List<FileItem> =
        path.listLegacyDirectoryEntries().mapNotNull(::loadEntryOrNull)

    private fun loadEntryOrNull(path: AppPath): FileItem? =
        try {
            path.loadFileItem()
        } catch (_: java.nio.file.NoSuchFileException) {
            // The entry changed between directory enumeration and metadata loading.
            null
        } catch (_: java.io.FileNotFoundException) {
            // Some providers report the same expected race as FileNotFoundException.
            null
        } catch (_: IOException) {
            com.wisso.wizefiles.util.AppLog.w(
                "FileList",
                "Skipping a directory entry whose attributes could not be read"
            )
            null
        } catch (_: SecurityException) {
            com.wisso.wizefiles.util.AppLog.w(
                "FileList",
                "Skipping a directory entry whose attributes are not accessible"
            )
            null
        }

    @Throws(IOException::class)
    fun createDirectory(path: AppPath) = path.createLegacyDirectory()

    @Throws(IOException::class)
    fun delete(path: AppPath) = path.deleteLegacyPath()

    @Throws(IOException::class)
    fun copy(source: AppPath, target: AppPath, vararg options: CopyOption) =
        source.copyLegacyPathTo(target, *options)

    @Throws(IOException::class)
    fun move(source: AppPath, target: AppPath, vararg options: CopyOption) =
        source.moveLegacyPathTo(target, *options)
}
