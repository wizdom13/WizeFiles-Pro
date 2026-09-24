package com.wisso.wizefiles.feature.filebrowser

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileListSelectionBottomPanelSourceTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `selection actions use the floating secondary container panel`() {
        val layout = sourceFile("app/src/main/res/layout/include_file_list_bottom_bar.xml")

        assertTrue("android:background=\"?colorSecondaryContainer\"" in layout)
        assertTrue("@+id/selectionActionLayout" in layout)
        assertTrue("@dimen/file_list_selection_action_bar_height" in layout)
        listOf(
            "selectionPrimaryAction1",
            "selectionPrimaryAction2",
            "selectionPrimaryAction3",
            "selectionPrimaryAction4",
            "selectionMoreAction"
        ).forEach { id ->
            assertTrue("@+id/$id" in layout)
        }
        assertTrue("@color/file_list_selection_action_tint" in layout)
        assertTrue("@drawable/ic_more_horizontal_white_24dp" in layout)
        assertTrue("android:maxLines=\"2\"" in layout)
        assertTrue("android:minHeight=\"@dimen/file_list_selection_action_bar_height\"" in layout)
    }

    @Test
    fun `selection owns the bottom panel while the top bar only shows its title`() {
        val source = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt"
        )
        val bottomPanelController = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListBottomPanelController.kt"
        )
        val selectionPanelController = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListSelectionPanelController.kt"
        )
        val updateBottomToolbar = source.substringAfter("private fun updateBottomToolbar()")
            .substringBefore("private fun onBottomToolbarNavigationIconClicked")

        assertTrue("bottomPanelController.render" in updateBottomToolbar)
        assertTrue("FileListBottomPanelCoordinator.resolveContent" in bottomPanelController)
        assertTrue("Content.Selection ->" in bottomPanelController)
        assertTrue("selectionPanelController.show(" in bottomPanelController)
        assertTrue("overlayActionMode.setMenuResource(0)" in source)
        assertTrue("configureSelectionMenu(menu, target, selectedFiles)" in source)
        assertTrue("PopupMenu(context, views.moreAction)" in selectionPanelController)
        assertTrue("updateBottomToolbar()" in source.substringAfter("onSelectedFilesChanged"))
        assertTrue("onPanelVisibilityChanged?.invoke(true)" in bottomPanelController)
        assertTrue("!isBottomPanelVisible" in source)
    }

    @Test
    fun `search selection dismisses the IME without collapsing search results`() {
        val fragment = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt"
        )
        val controller = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListSearchController.kt"
        )
        val searchView = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/ui/FixQueryChangeSearchView.kt"
        )
        val backCallback = controller.substringAfter(
            "val onBackPressedCallback"
        ).substringBefore("fun bind")
        val selectionChanged = fragment.substringAfter(
            "private fun onSelectedFilesChanged"
        ).substringBefore("private fun updateOverlayToolbar")

        assertTrue("hideImePreservingSearch()" in backCallback)
        assertTrue(
            backCallback.indexOf("hideImePreservingSearch()") <
                backCallback.indexOf("collapse()")
        )
        assertTrue("hideSearchImeForSelection()" in selectionChanged)
        assertTrue("searchController.hideImePreservingSearch()" in fragment)
        val actionCollapse = controller.substringAfter(
            "override fun onMenuItemActionCollapse"
        ).substringBefore("view.setOnQueryTextListener")

        assertTrue("fun hideImePreservingSearch(): Boolean" in searchView)
        assertTrue("override fun clearFocus()" in searchView)
        assertTrue("preserveSearchUntilUptimeMillis" in searchView)
        assertTrue("IME_DISMISSAL_GRACE_MILLIS" in searchView)
        assertTrue("view.hideImePreservingSearch()" in actionCollapse)
        assertTrue("return false" in actionCollapse)
        assertTrue(
            actionCollapse.indexOf("view.hideImePreservingSearch()") <
                actionCollapse.indexOf("preserveSessionOnCollapse")
        )
        assertFalse("collapseOnNextClearFocus" in searchView)
        assertFalse("onBackCollapseRequested" in searchView)
        assertFalse("onBackCollapseRequested" in fragment)
        assertFalse("onBackCollapseRequested" in controller)
    }

    @Test
    fun `selection panel chooses folder safe contextual actions without overflow duplication`() {
        val menuConfiguration = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/BrowserSelectionMenuConfigurator.kt"
        )
        val controller = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListSelectionPanelController.kt"
        )

        assertTrue(
            "menu.findItem(R.id.action_share).isVisible = !containsDirectory" in
                menuConfiguration
        )
        assertTrue("item.isVisible = false" in controller)
        assertTrue("IntArray(views.primaryActions.size)" in controller)
    }

    @Test
    fun `row overflow actions are centralized in the selection menu`() {
        val adapter = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListAdapter.kt"
        )
        val listLayout = sourceFile("app/src/main/res/layout/item_file_list.xml")
        val gridLayout = sourceFile("app/src/main/res/layout/item_file_grid.xml")
        val menu = sourceFile("app/src/main/res/menu/menu_file_list_select.xml")
        val fragment = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt"
        ) + sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/BrowserSelectionMenuConfigurator.kt"
        )
        val vaultAdapter = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/vault/VaultEntryListAdapter.kt"
        )
        val vaultLayout = sourceFile("app/src/main/res/layout/item_vault_entry_list.xml")

        assertFalse("menu_file_item" in adapter)
        assertFalse("menuButton" in adapter)
        assertFalse("@+id/menuButton" in listLayout)
        assertFalse("@+id/menuButton" in gridLayout)
        assertFalse(File(root, "app/src/main/res/menu/menu_file_item.xml").exists())
        assertTrue("ItemVaultEntryListBinding" in vaultAdapter)
        assertFalse("ItemFileListBinding" in vaultAdapter)
        assertFalse("menuButton" in vaultAdapter)
        assertFalse("@+id/menuButton" in vaultLayout)
        assertTrue("selectedEntryIds" in vaultAdapter)
        listOf(
            "action_open_with",
            "action_rename",
            "action_copy_path",
            "action_add_bookmark",
            "action_create_shortcut",
            "action_properties"
        ).forEach { id ->
            assertTrue("@+id/$id" in menu)
            assertTrue("R.id.$id" in fragment)
        }
    }

    @Test
    fun `selected files use secondary container content colors`() {
        val background = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/ui/CheckableItemBackground.kt"
        )
        val adapter = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListAdapter.kt"
        )
        assertTrue("colorSecondaryContainer" in background)
        assertFalse("withModulatedAlpha(0.12f)" in background)
        assertTrue("colorOnSecondaryContainer" in adapter)
    }

    private fun sourceFile(path: String): String = File(root, path).readText()
}
