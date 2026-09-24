// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FileListDeleteOptionsSourceTest {

    @Test
    fun fileListDeleteFlowUsesSharedDeleteOptionsPlumbing() {
        val fragment = sourceFile("FileListFragment.kt").readText()
        val launcher = sourceFile("BrowserOperationLauncher.kt").readText()
        val controller = sourceFile("FileListOperationController.kt").readText()

        assertTrue(fragment.contains("operationLauncher.confirmDelete(files)"))
        assertTrue(launcher.contains("controller.confirmDelete(files)"))
        assertTrue(
            launcher.contains(
                "showDeleteConfirmation(files, mode, supportsSecureShred, options)"
            )
        )
        assertTrue(controller.contains("DeleteOptionsSupport.targetMode(paths)"))
        assertTrue(controller.contains("DeleteOptionsSupport.supportsSecureShred(paths)"))
        assertTrue(controller.contains("DeleteConfirmationSessionStore.get(deleteTargetMode)"))
        assertTrue(controller.contains("options.copy(permanentDelete = false)"))
        assertTrue(controller.contains("supportedRememberedOptions?.secureShred == true && !supportsSecureShred"))
        assertTrue(controller.contains("context.showToast(R.string.delete_option_secure_shred_session_fallback_message)"))
        assertTrue(controller.contains("DeleteConfirmationSessionStore.update("))
        assertTrue(controller.contains("DeleteOptionsSupport.targetMode(paths),"))
        assertTrue(controller.contains("val paths = fileItemPathsForJob(files)"))
        assertTrue(controller.contains("FileOperationService.delete(paths, context, options)"))
    }

    @Test
    fun confirmDeleteDialogUsesSharedDeleteOptionsDialog() {
        val source = sourceFile("ConfirmDeleteFilesDialogFragment.kt").readText()

        assertTrue(source.contains("DeleteOptionsDialog.create("))
        assertTrue(source.contains("permanentDeleteVisible = localTrashOptions"))
        assertTrue(source.contains("secureShredVisible = localTrashOptions"))
        assertTrue(source.contains("delete_option_remote_permanent_warning"))
        assertTrue(source.contains("listener.deleteFiles(files, options)"))
    }

    private fun sourceFile(name: String): File = listOf(
        File("src/main/java/com/wisso/wizefiles/feature/filebrowser/$name"),
        File("app/src/main/java/com/wisso/wizefiles/feature/filebrowser/$name")
    ).firstOrNull { it.exists() } ?: error("Missing source file: $name")
}
