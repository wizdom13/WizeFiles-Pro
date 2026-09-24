// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import com.wisso.wizefiles.feature.filejobs.DeleteOptions
import com.wisso.wizefiles.feature.filejobs.DeleteTargetMode
import java.nio.file.Path

/** Browser-facing mutation facade; dialogs remain supplied by the UI as explicit launch ports. */
internal class BrowserOperationLauncher(
    private val controller: FileListOperationController,
    private val showDeleteConfirmation: (
        FileItemSet,
        DeleteTargetMode,
        Boolean,
        DeleteOptions
    ) -> Unit
) {
    fun cut(files: FileItemSet) = controller.cut(files)
    fun copy(files: FileItemSet) = controller.copy(files)
    fun paste(directory: Path) = controller.paste(directory)
    fun delete(files: FileItemSet, options: DeleteOptions) = controller.delete(files, options)
    fun restore(files: FileItemSet) = controller.restore(files)
    fun restoreAll() = controller.restoreAll()
    fun confirmDeleteAll() = controller.confirmDeleteAll()
    fun extract(files: FileItemSet) = controller.extract(files)

    fun confirmDelete(files: FileItemSet) {
        controller.confirmDelete(files) { mode, supportsSecureShred, options ->
            showDeleteConfirmation(files, mode, supportsSecureShred, options)
        }
    }
}
