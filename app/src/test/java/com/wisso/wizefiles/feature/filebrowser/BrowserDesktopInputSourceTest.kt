// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserDesktopInputSourceTest {
    @Test fun `desktop commands use the shared router and protect text input`() {
        val activity = projectFile("src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListActivity.kt") +
            projectFile("src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListActivityInfrastructure.kt")
        assertTrue(activity.contains("event.isCtrlPressed"))
        assertTrue(activity.contains("BrowserCommand.PASTE"))
        assertTrue(activity.contains("KEYCODE_FORWARD_DEL"))
        assertTrue(activity.contains("KEYCODE_F2"))
        assertTrue(activity.contains("isEditableInputFocused"))
        assertTrue(activity.contains("onProvideKeyboardShortcuts"))
    }

    @Test fun `right click delegates item and background menus`() {
        val adapter = projectFile("src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListAdapter.kt")
        val fragment = projectFile("src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt")
        assertTrue(adapter.contains("setOnContextClickListener"))
        assertTrue(adapter.contains("BUTTON_SECONDARY"))
        assertTrue(fragment.contains("showContextMenu(file: FileItem"))
        assertTrue(fragment.contains("menu_file_list_context_background"))
    }

    private fun projectFile(path: String): String =
        listOf(File(path), File("app/$path")).firstOrNull(File::exists)?.readText()
            ?: error("Missing $path")
}
