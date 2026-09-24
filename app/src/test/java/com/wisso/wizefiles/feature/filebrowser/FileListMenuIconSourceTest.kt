// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileListMenuIconSourceTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `file browser destinations use distinct themed icons`() {
        val navigation = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/navigation/NavigationItems.kt"
        )
        val iconNames = listOf(
            "ic_transfer_center_control_normal_24dp",
            "ic_sync_backup_control_normal_24dp",
            "ic_nearby_transfer_control_normal_24dp"
        )

        iconNames.forEach { iconName ->
            assertTrue("R.drawable.$iconName" in navigation)
            val icon = sourceFile("app/src/main/res/drawable/$iconName.xml")
            assertTrue("android:tint=\"?colorControlNormal\"" in icon)
            assertTrue("android:fillColor=\"@android:color/white\"" in icon)
        }
        val toolItems = navigation.substringAfter("private val toolMenuItems")
            .substringBefore("private val settingsMenuItems")
        assertFalse("R.drawable.ic_transfer_control_normal_24dp" in toolItems)
    }

    @Test
    fun `browser menus tint every icon with a state aware primary color`() {
        listOf(
            "app/src/main/res/menu/menu_file_list.xml",
            "app/src/main/res/menu/menu_file_list_select.xml"
        ).forEach { path ->
            val menu = sourceFile(path)

            assertEquals(
                Regex("""android:icon="[^"]+"""").findAll(menu).count(),
                Regex("""app:iconTint="@color/file_browser_menu_icon_tint"""")
                    .findAll(menu)
                    .count()
            )
        }

        val tint = sourceFile("app/src/main/res/color/file_browser_menu_icon_tint.xml")
        assertTrue("android:state_enabled=\"false\"" in tint)
        assertTrue("android:alpha=\"0.38\"" in tint)
        assertTrue("android:color=\"?attr/colorPrimary\"" in tint)
    }

    @Test
    fun `browser destinations are grouped in the navigation drawer`() {
        val menu = sourceFile("app/src/main/res/menu/menu_file_list.xml")
        val navigation = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/navigation/NavigationItems.kt"
        )
        val tools = navigation.substringAfter("private val toolMenuItems")
            .substringBefore("private val settingsMenuItems")
        val drawerItems = navigation
            .substringAfter("val navigationItems: List<NavigationItem?>")
            .substringBefore("private val storageItems")
            .lines()
            .map(String::trim)
        val transferTrashGroup = listOf(
            "add(null)",
            "add(transferCenterMenuItem)",
            "recycleBinItem?.let { add(it) }",
            "add(null)",
            "addAll(toolMenuItems)"
        )

        assertFalse("@+id/action_tools" in menu)
        listOf(
            "NavigationAction.STORAGE_CLEANER",
            "NavigationAction.SYNC_BACKUP",
            "NavigationAction.LOCAL_SHARING",
            "NavigationAction.NEARBY_TRANSFER"
        ).forEach { action ->
            assertTrue(action in tools)
        }
        assertFalse("NavigationAction.TRANSFER_CENTER" in tools)
        assertTrue("private val transferCenterMenuItem" in navigation)
        assertTrue(drawerItems.windowed(transferTrashGroup.size).any { it == transferTrashGroup })
        assertTrue("R.drawable.ic_storage_treemap_24dp" in tools)
        assertTrue("@+id/action_sync_panes" in menu)
    }

    @Test
    fun `open-as overflow icon uses the menu theme tint`() {
        val icon = sourceFile("app/src/main/res/drawable/ic_open_as.xml")

        assertTrue("android:tint=\"?colorControlNormal\"" in icon)
        assertFalse("@color/activity_icon_tint" in icon)
    }

    private fun sourceFile(path: String): String = File(root, path).readText()
}
