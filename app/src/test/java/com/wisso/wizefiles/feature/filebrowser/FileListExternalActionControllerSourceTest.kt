package com.wisso.wizefiles.feature.filebrowser

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileListExternalActionControllerSourceTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `fragment delegates file launch and external actions to lifecycle controller`() {
        val fragment = source("FileListFragment.kt")
        val controller = source("FileListExternalActionController.kt")

        assertTrue("externalActionController.open(file)" in fragment)
        assertTrue("externalActionController.installApk(file)" in fragment)
        assertTrue("externalActionController.rename(file, newName)" in fragment)
        assertTrue("InternalOpenPolicy.targetAfterExtraction" in controller)
        assertTrue("InternalOpenIntents.create" in controller)
        assertTrue("FileOperationService.openInternalViewer" in controller)
        assertTrue("ShortcutManagerCompat.requestPinShortcut" in controller)
        assertTrue("BookmarkDirectories.add" in controller)
        assertFalse("private fun openFileWithIntent" in fragment)
        assertFalse("ShortcutInfoCompat.Builder" in fragment)
    }

    @Test
    fun `controller releases view lifecycle callbacks`() {
        val controller = source("FileListExternalActionController.kt")
        val release = controller.substringAfter("fun release() {").substringBefore("}")

        assertTrue("showOpenApkDialog = null" in release)
        assertTrue("confirmReplace = null" in release)
        assertTrue("navigateTo = null" in release)
        assertTrue("pickFiles = null" in release)
        assertTrue("context = null" in release)
    }

    private fun source(name: String): String = File(
        root,
        "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/$name"
    ).readText()
}
