package com.wisso.wizefiles.navigation

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationDrawerRootsRegressionSourceTest {

    @Test
    fun `drawer keeps built in roots ahead of connect storage`() {
        val navigationItems = sourceFile("src/main/java/com/wisso/wizefiles/feature/navigation/NavigationItems.kt")

        assertTrue(navigationItems.contains("addAll(builtInRootStorageItems)"))
        assertTrue(navigationItems.contains("add(AddStorageItem())"))
    }

    @Test
    fun `built in roots are force included and deduplicated from regular storage list`() {
        val navigationItems = sourceFile("src/main/java/com/wisso/wizefiles/feature/navigation/NavigationItems.kt")

        assertTrue(navigationItems.contains("private val builtInRootStorageIds"))
        assertTrue(
            navigationItems.contains(
                "it.isVisible && it !is VaultStorage && it.id !in builtInRootStorageIds"
            )
        )
        assertTrue(navigationItems.contains("FileSystemRoot(null, true)"))
        assertTrue(navigationItems.contains("PrimaryStorageVolume(null, true)"))
        assertTrue(navigationItems.contains("isSuAvailable = LibSuFileServiceLauncher.isSuAvailable()"))
        assertTrue(navigationItems.contains("if (isSuAvailable)"))
        assertTrue(navigationItems.contains("): List<DeviceStorage>"))
    }

    @Test
    fun `trash root uses friendly localized labels in direct navigation`() {
        val breadcrumb = sourceFile(
            "src/main/java/com/wisso/wizefiles/feature/filebrowser/BreadcrumbLiveData.kt"
        )
        val activity = sourceFile(
            "src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListActivity.kt"
        )
        val manager = sourceFile(
            "src/main/java/com/wisso/wizefiles/data/recyclebin/RecycleBinManager.kt"
        )

        assertTrue(manager.contains(".wizefiles_trash_bin"))
        assertTrue(breadcrumb.contains("RecycleBinManager.isRecycleBinRootPath(path)"))
        assertTrue(breadcrumb.contains("R.string.navigation_recycle_bin"))
        assertTrue(activity.contains("RecycleBinManager.isRecycleBinRootPath(path)"))
        assertTrue(activity.contains("R.string.navigation_recycle_bin"))
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
