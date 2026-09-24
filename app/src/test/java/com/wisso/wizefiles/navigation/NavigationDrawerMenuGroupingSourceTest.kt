package com.wisso.wizefiles.navigation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationDrawerMenuGroupingSourceTest {

    @Test
    fun `screenshots bookmark is hidden from the drawer`() {
        val navigationItems = sourceFile("src/main/java/com/wisso/wizefiles/feature/navigation/NavigationItems.kt")

        assertTrue(navigationItems.contains("EnvironmentCompat2.DIRECTORY_SCREENSHOTS"))
        assertTrue(navigationItems.contains(".filterNot { isScreenshotsBookmark(it) }"))
    }

    @Test
    fun `transfer center and trash bin share a dedicated drawer group`() {
        val navigationItems = sourceFile("src/main/java/com/wisso/wizefiles/feature/navigation/NavigationItems.kt")
        val expectedGroup =
            "add(null)\n" +
                "            add(transferCenterMenuItem)\n" +
                "            recycleBinItem?.let { add(it) }\n" +
                "            add(null)\n" +
                "            addAll(toolMenuItems)"

        assertTrue(navigationItems.contains(expectedGroup))
        assertTrue(navigationItems.contains("private val transferCenterMenuItem"))

        val toolMenuStart = navigationItems.indexOf("private val toolMenuItems")
        val settingsMenuStart = navigationItems.indexOf("private val settingsMenuItems")
        assertTrue(toolMenuStart >= 0)
        assertTrue(settingsMenuStart > toolMenuStart)
        val toolMenuBlock = navigationItems.substring(toolMenuStart, settingsMenuStart)
        assertFalse(toolMenuBlock.contains("NavigationAction.TRANSFER_CENTER"))
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
