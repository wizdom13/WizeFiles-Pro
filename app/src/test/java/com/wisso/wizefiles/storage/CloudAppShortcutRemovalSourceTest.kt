package com.wisso.wizefiles.storage

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudAppShortcutRemovalSourceTest {

    @Test
    fun `connect storage offers native cloud accounts without external app shortcuts`() {
        val dialog = projectFile(
            "src/main/java/com/wisso/wizefiles/feature/storage/AddStorageDialogFragment.kt"
        ).readText()

        assertTrue(dialog.contains("R.string.storage_add_storage_rclone"))
        assertTrue(dialog.contains("R.string.storage_add_storage_saf_folder"))
        assertFalse(dialog.contains("AddCloudAppStorageActivity"))
        assertFalse(dialog.contains("CloudAppProvider.GOOGLE_DRIVE"))
        assertFalse(dialog.contains("CloudAppProvider.DROPBOX"))
    }

    @Test
    fun `shortcut only activities models and resources are removed`() {
        val removedClasses = listOf(
            "AddCloudAppStorageActivity.kt",
            "CloudAppDispatchActivity.kt",
            "CloudAppProvider.kt",
            "CloudAppStorage.kt",
            "EditCloudAppStorageDialogActivity.kt",
            "EditCloudAppStorageDialogFragment.kt",
            "InstalledCloudApps.kt"
        )
        val storageDirectory = projectFile(
            "src/main/java/com/wisso/wizefiles/feature/storage/AddStorageDialogFragment.kt"
        ).parentFile
        removedClasses.forEach { fileName ->
            assertFalse(File(storageDirectory, fileName).exists())
        }

        val manifest = projectFile("src/main/AndroidManifest.xml").readText()
        val strings = projectFile("src/main/res/values/strings.xml").readText()
        assertFalse(manifest.contains("CloudApp"))
        assertFalse(strings.contains("storage_add_storage_google_drive"))
        assertFalse(strings.contains("storage_add_storage_dropbox"))
        assertFalse(strings.contains("storage_cloud_app_not_installed"))
    }

    private fun projectFile(path: String): File {
        val direct = File(path)
        if (direct.exists()) return direct
        val fromRepoRoot = File("app", path)
        if (fromRepoRoot.exists()) return fromRepoRoot
        throw java.io.FileNotFoundException(path)
    }
}
