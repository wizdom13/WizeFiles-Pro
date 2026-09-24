// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.batchrename

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchRenameIntegrationSourceTest {
    @Test
    fun `action is available only for multiple writable files`() {
        assertTrue(BatchRenameAvailability.isAvailable(2, false, false, false, false, false))
        assertFalse(BatchRenameAvailability.isAvailable(1, false, false, false, false, false))
        assertFalse(BatchRenameAvailability.isAvailable(2, true, false, false, false, false))
        assertFalse(BatchRenameAvailability.isAvailable(2, false, true, false, false, false))
        assertFalse(BatchRenameAvailability.isAvailable(2, false, false, true, false, false))
        assertFalse(BatchRenameAvailability.isAvailable(2, false, false, false, true, false))
        assertFalse(BatchRenameAvailability.isAvailable(2, false, false, false, false, true))
    }

    @Test
    fun `selection menu opens session based batch rename screen`() {
        val fragment = File(
            "src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt"
        ).readText() + File(
            "src/main/java/com/wisso/wizefiles/feature/filebrowser/BrowserSelectionMenuConfigurator.kt"
        ).readText()
        val menu = File("src/main/res/menu/menu_file_list_select.xml").readText()

        assertTrue(menu.contains("action_batch_rename"))
        assertTrue(menu.contains("@string/batch_rename_title"))
        assertTrue(fragment.contains("BatchRenameAvailability.isAvailable("))
        assertTrue(fragment.contains("BatchRenameSessionStore.create("))
        assertTrue(fragment.contains("BatchRenameActivity.createIntent(requireContext(), sessionId)"))
    }

    @Test
    fun `executor uses temporary names and two phase rollback`() {
        val executor = File(
            "src/main/java/com/wisso/wizefiles/feature/filejobs/FileOpenRenameJobs.kt"
        ).readText()

        assertTrue(executor.contains("class BatchRenameFileOperationJob"))
        assertTrue(executor.contains(".wizefiles-\$purpose-"))
        assertTrue(executor.contains("states.forEach { state ->"))
        assertTrue(executor.contains("rollback(states, stagedCount, completedCount)"))
        assertTrue(executor.contains("state.rollbackPath = rollbackPath"))
        assertTrue(executor.contains("rename(rollbackPath, state.originalPath.fileName.toString())"))
        assertTrue(executor.contains("notifyFileListRefresh()"))
    }

    @Test
    fun `batch rename screen respects system bars and keeps preview visible`() {
        val activity = File(
            "src/main/java/com/wisso/wizefiles/batchrename/BatchRenameActivity.kt"
        ).readText()
        val layout = File("src/main/res/layout/activity_batch_rename.xml").readText()
        val strings = File("src/main/res/values/strings.xml").readText()

        assertTrue(activity.contains("WindowInsetsCompat.Type.statusBars()"))
        assertTrue(activity.contains("WindowInsetsCompat.Type.navigationBars()"))
        assertTrue(activity.contains("binding.renameActionBar"))
        assertTrue(activity.contains("binding.previewList.scrollToPosition(0)"))
        assertTrue(activity.contains("plan.changesExtensions()"))
        assertTrue(layout.contains("android:id=\"@+id/renameActionBar\""))
        val previewList = layout.substringAfter("android:id=\"@+id/previewList\"")
            .substringBefore("/>")
        assertTrue(previewList.contains("android:paddingTop=\"8dp\""))
        assertTrue(strings.contains("batch_rename_extension_warning"))
    }
}
