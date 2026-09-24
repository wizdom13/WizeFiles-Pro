// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserWorkspaceCoordinatorSourceTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `fragment delegates dual pane state transitions`() {
        val fragment = source("FileListFragment.kt")
        val controller = source("FileListWorkspaceController.kt")
        val coordinator = source("BrowserWorkspaceCoordinator.kt")

        assertTrue("workspaceController.updatePresentation" in fragment)
        assertTrue("workspaceController.activate" in fragment)
        assertTrue("workspaceController.synchronizeActivePane" in fragment)
        assertTrue("coordinator.updateVisibility" in controller)
        assertTrue("coordinator.activate" in controller)
        assertTrue("coordinator.synchronizeActivePane" in controller)
        assertTrue("secondaryPaneRequired" in coordinator)
        assertFalse("this.activePane =" in fragment)
        assertFalse("this.dualPaneVisible =" in fragment)
    }

    private fun source(name: String): String = File(
        root,
        "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/$name"
    ).readText()
}
