// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class BreadcrumbFriendlyNamingRegressionSourceTest {

    @Test
    fun `breadcrumb lookup uses navigation root helper instead of direct path map index`() {
        val source = sourceFile("src/main/java/com/wisso/wizefiles/feature/filebrowser/BreadcrumbLiveData.kt")

        assertTrue(source.contains("findNavigationRoot(path, navigationRootMap)"))
    }


    @Test
    fun `navigate up trims child breadcrumb entries`() {
        val source = sourceFile("src/main/java/com/wisso/wizefiles/feature/filebrowser/TrailData.kt")

        assertTrue(source.contains("trail.subList(0, newIndex + 1).toList()"))
        assertTrue(source.contains("states.subList(0, newIndex + 1).toMutableList()"))
    }

    @Test
    fun `open directory picker uses localized chooser title`() {
        val source = sourceFile(
            "src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListPickerCoordinator.kt"
        )
        val pickerTitle = source.substringAfter("fun resolveTitle")
            .substringBefore("fun resolveDirectoryConfirmationLabel")

        assertTrue("PickOptions.Mode.OPEN_DIRECTORY ->" in pickerTitle)
        assertTrue("getString(R.string.file_list_choose_directory)" in pickerTitle)
    }

    private fun sourceFile(path: String): String {
        val direct = File(path)
        if (direct.exists()) {
            return direct.readText()
        }
        val fromRepoRoot = File("app", path)
        if (fromRepoRoot.exists()) {
            return fromRepoRoot.readText()
        }
        throw java.io.FileNotFoundException(path)
    }
}
