// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.archive.editor

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveEditingIntegrationSourceTest {
    private val project = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test fun `archive provider remains read only and mutations are transactional`() {
        val provider = project.resolve(
            "app/src/main/java/com/wisso/wizefiles/data/providers/archive/ArchiveFileSystemProvider.kt"
        ).readText()
        val engine = project.resolve(
            "app/src/main/java/com/wisso/wizefiles/data/providers/archive/editor/ArchiveRewriteEngine.kt"
        ).readText()
        assertTrue(provider.contains("throw ReadOnlyFileSystemException"))
        assertTrue(engine.contains("ArchiveEditPhase.VALIDATING"))
        assertTrue(engine.contains("StandardCopyOption.ATOMIC_MOVE"))
        assertTrue(engine.contains("ORIGINAL_BACKED_UP"))
    }

    @Test fun `encrypted and signed inputs cannot enter the writer`() {
        val engine = project.resolve(
            "app/src/main/java/com/wisso/wizefiles/data/providers/archive/editor/ArchiveRewriteEngine.kt"
        ).readText()
        val capability = project.resolve(
            "app/src/main/java/com/wisso/wizefiles/data/providers/archive/editor/ArchiveEditCapabilities.kt"
        ).readText()
        assertTrue(engine.contains("Encrypted archives are read-only"))
        assertTrue(capability.contains(".apk"))
        assertTrue(capability.contains("Split archives are read-only"))
        assertFalse(engine.contains("password = spec"))
    }

    @Test fun `browser actions share the archive operation pipeline`() {
        val service = project.resolve(
            "app/src/main/java/com/wisso/wizefiles/feature/filejobs/FileOperationService.kt"
        ).readText() + project.resolve(
            "app/src/main/java/com/wisso/wizefiles/feature/filejobs/FileOperationCommandCoordinator.kt"
        ).readText()
        assertTrue(service.contains("TransferOperationType.ARCHIVE_MODIFY"))
        assertTrue(service.contains("ArchiveMutationType.ADD"))
        assertTrue(service.contains("ArchiveMutationType.DELETE"))
        assertTrue(service.contains("ArchiveMutationType.RENAME"))
        assertTrue(service.contains("ArchiveMutationType.CREATE_DIRECTORY"))
    }

    @Test fun `runtime archive editing integration is complete`() {
        val browser = project.resolve(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt"
        ).readText()
        val selectionPanel = project.resolve(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListSelectionPanelController.kt"
        ).readText()
        val bottomPanel = project.resolve(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListBottomPanelController.kt"
        ).readText()
        val operations = project.resolve(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListOperationController.kt"
        ).readText()
        val browserMenu = project.resolve(
            "app/src/main/res/menu/menu_file_list.xml"
        ).readText()
        val nativeBridge = listOf(
            "archive_jni.c",
            "archive_jni_callbacks.c",
            "archive_jni_reader.c",
            "archive_jni_writer.c",
            "archive_jni_metadata.c",
            "archive_jni_registration.c"
        ).joinToString(separator = "\n") { unit ->
            project.resolve("app/src/main/cpp/libarchive_jni/$unit").readText()
        }
        val engine = project.resolve(
            "app/src/main/java/com/wisso/wizefiles/data/providers/archive/editor/ArchiveRewriteEngine.kt"
        ).readText()
        val operationJob = project.resolve(
            "app/src/main/java/com/wisso/wizefiles/feature/filejobs/FileOperationJob.kt"
        ).readText()
        val details = project.resolve(
            "app/src/main/java/com/wisso/wizefiles/feature/details/basic/FilePropertiesBasicTabFragment.kt"
        ).readText()

        assertTrue(browser.contains("R.string.archive_edit_paste_here"))
        assertTrue(browserMenu.contains("android:id=\"@+id/action_paste\""))
        assertTrue(browser.contains("updatePasteMenuItem()"))
        assertTrue(browser.contains("pasteItem.isVisible = isBrowserCommandAvailable"))
        assertFalse(browser.contains("pasteItem.isEnabled = isBrowserCommandAvailable"))
        assertTrue(browser.contains("(requireActivity() as MenuHost).invalidateMenu()"))
        assertTrue(operations.contains("paths.all { it.isArchivePath }"))
        val bottomActionModeCleanup = bottomPanel
            .substringAfter("override fun onToolbarActionModeFinished")
            .substringBefore("}")
        assertTrue(bottomActionModeCleanup.contains("selectionPanelController.showToolbarContent()"))
        assertTrue(bottomActionModeCleanup.contains("onPanelVisibilityChanged?.invoke(false)"))
        assertTrue(selectionPanel.contains("popupMenu?.dismiss()"))
        listOf(
            "setAtime", "setBirthtime", "setUname", "setGid", "setGname", "setSymlink"
        ).forEach { method ->
            assertTrue(nativeBridge.contains("{\"$method\""))
        }
        assertTrue(engine.contains("lockInterruptibly()"))
        assertTrue(operationJob.contains("catch (e: LinkageError)"))
        assertFalse(details.contains("as ArchiveFileAttributes"))
    }
}
