package com.wisso.wizefiles.feature.filebrowser

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserToolsAndEditMenuSourceTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `tools move to the drawer in the approved order`() {
        val menu = source("app/src/main/res/menu/menu_file_list.xml")
        val navigation = source(
            "app/src/main/java/com/wisso/wizefiles/feature/navigation/NavigationItems.kt"
        )
        val fragment = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt"
        )
        val toolItems = navigation.substringAfter("private val toolMenuItems")
            .substringBefore("private val settingsMenuItems")
        val orderedActions = listOf(
            "NavigationAction.STORAGE_CLEANER",
            "NavigationAction.SYNC_BACKUP",
            "NavigationAction.LOCAL_SHARING",
            "NavigationAction.NEARBY_TRANSFER",
            "R.string.navigation_app_manager"
        )
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

        assertFalse("action_tools" in menu)
        assertFalse("action_storage_cleaner" in menu)
        assertFalse("action_transfer_center" in menu)
        assertFalse("action_sync_backup" in menu)
        assertFalse("action_local_sharing" in menu)
        assertFalse("action_nearby_transfer" in menu)
        orderedActions.forEach { action -> assertTrue(action in toolItems) }
        orderedActions.zipWithNext().forEach { (first, second) ->
            assertTrue(toolItems.indexOf(first) < toolItems.indexOf(second))
        }
        assertFalse("NavigationAction.TRANSFER_CENTER" in toolItems)
        assertTrue("private val transferCenterMenuItem" in navigation)
        assertTrue(drawerItems.windowed(transferTrashGroup.size).any { it == transferTrashGroup })
        assertTrue("addAll(toolMenuItems)" in navigation)
        assertTrue("addAll(settingsMenuItems)" in navigation)
        assertTrue(
            navigation.indexOf("addAll(toolMenuItems)") <
                navigation.indexOf("addAll(settingsMenuItems)")
        )
        assertTrue("override fun launchNavigationAction(action: NavigationAction)" in fragment)
        assertTrue("activePaneFragment().viewModel.currentPath.toAppPath()" in fragment)
    }

    @Test
    fun `single supported mutable text selection exposes direct edit action`() {
        val menu = source("app/src/main/res/menu/menu_file_list_select.xml")
        val configurator = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/BrowserSelectionMenuConfigurator.kt"
        )
        val actionRouter = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/BrowserSelectionActionRouter.kt"
        )
        val fragment = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt"
        )
        val selectionCoordinator = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/BrowserSelectionCoordinator.kt"
        )

        assertTrue("@+id/action_edit" in menu)
        assertTrue("singleFile.mimeType.isTextEditorSupported" in configurator)
        assertTrue("isMutableFile(singleFile)" in configurator)
        assertTrue("R.id.action_edit ->" in actionRouter)
        assertTrue("BrowserDirectSelectionAction.EDIT" in actionRouter)
        assertTrue("BrowserDirectSelectionAction.EDIT ->" in selectionCoordinator)
        assertTrue("single(files, effects.edit)" in selectionCoordinator)
        assertTrue("TextEditorActivity.createIntent(it.path)" in fragment)
    }

    @Test
    fun `paste menu tolerates preparation before the path is initialized`() {
        val fragment = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt"
        )
        val pasteMenu = fragment.substringAfter(
            "private fun updatePasteMenuItem"
        ).substringBefore("private fun updateBottomToolbar")

        assertTrue(
            "val currentPath = target.viewModel.currentPathLiveData.value" in fragment
        )
        assertTrue(
            "currentLocationWritable = currentPath?.let(::isWritableBrowserLocation) == true" in
                fragment
        )
        assertTrue("currentPathLiveData.value" in pasteMenu)
        assertTrue("val isArchivePasteDestination = currentPath?.let {" in pasteMenu)
        assertTrue("ArchiveEditCapabilities.isEditableLocation(it)" in pasteMenu)
        assertTrue("if (isArchivePasteDestination)" in pasteMenu)
        assertFalse(
            "ArchiveEditCapabilities.isEditableLocation(target.viewModel.currentPath)" in pasteMenu
        )
    }

    private fun source(path: String): String = File(root, path).readText()
}
