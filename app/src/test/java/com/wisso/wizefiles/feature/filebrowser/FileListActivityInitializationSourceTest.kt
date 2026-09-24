// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FileListActivityInitializationSourceTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `restored fragments cannot access browser fragment before gated initialization`() {
        val activity = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListActivity.kt"
        )
        val dualPane = activity.substringAfter(
            "internal fun isDualPaneEnabled(owner: FileListFragment)"
        ).substringBefore("internal fun isPaneActive")

        assertTrue("if (::fragment.isInitialized) fragment else null" in activity)
        assertTrue("activeBrowserFragmentOrNull() ?: return false" in dualPane)
        assertTrue("activeFragment?.onKeyShortcut" in activity)
        assertTrue("activeFragment != null" in activity)
    }

    private fun sourceFile(path: String): String = File(root, path).readText()
}
