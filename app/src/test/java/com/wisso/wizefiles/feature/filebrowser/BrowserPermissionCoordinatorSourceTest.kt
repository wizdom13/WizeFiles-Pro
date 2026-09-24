package com.wisso.wizefiles.feature.filebrowser

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPermissionCoordinatorSourceTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `permission state machines are outside the fragment`() {
        val fragment = source("FileListFragment.kt")
        val host = source("FileListPermissionFragment.kt")
        val coordinator = source("BrowserPermissionCoordinator.kt")

        assertTrue("FileListPermissionFragment()" in fragment)
        assertTrue("permissionCoordinator.ensureStorageAccess()" in host)
        assertTrue("onNotificationPermissionResult" in host)
        assertFalse("requestAllFilesAccessLauncher" in fragment)
        assertFalse("onShowRequestStoragePermissionRationaleResult" in fragment)
        assertTrue("Environment.isExternalStorageManager()" in coordinator)
        assertTrue("isStorageAccessRequested" in coordinator)
        assertTrue("isNotificationPermissionRequested" in coordinator)
        assertTrue("RequestPermissionInSettingsContract" in coordinator)
        assertFalse("private class RequestAllFilesAccessContract" in fragment)
    }

    private fun source(name: String): String = File(
        root,
        "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/$name"
    ).readText()
}
