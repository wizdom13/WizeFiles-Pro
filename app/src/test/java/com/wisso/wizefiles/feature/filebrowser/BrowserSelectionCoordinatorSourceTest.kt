package com.wisso.wizefiles.feature.filebrowser

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserSelectionCoordinatorSourceTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `selection policy and package actions are outside the fragment`() {
        val fragment = source("FileListFragment.kt")
        val menu = source("BrowserSelectionMenuConfigurator.kt")
        val packages = source("BrowserPackageSelectionActionHandler.kt")

        assertTrue("BrowserSelectionMenuConfigurator.configure" in fragment)
        assertTrue("BrowserPackageSelectionActionHandler.handle" in fragment)
        assertTrue("BatchRenameAvailability.isAvailable" in menu)
        assertTrue("action_copy_to_other_pane" in menu)
        assertTrue("ApkSignVerifyActivity.createSignIntent" in packages)
        assertTrue("APKM_IMPORT" in packages)
        assertFalse("val singleApks" in fragment)
    }

    private fun source(name: String): String = File(
        root,
        "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/$name"
    ).readText()
}
