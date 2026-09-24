// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.vault

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultSelectionSourceTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `vault selection matches browser interaction and deletion is confirmed`() {
        val activity = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/vault/VaultActivity.kt"
        )
        val layout = sourceFile("app/src/main/res/layout/activity_vault.xml")
        val row = sourceFile("app/src/main/res/layout/item_vault_entry_list.xml")
        val gridRow = sourceFile("app/src/main/res/layout/item_vault_entry_grid.xml")
        val adapter = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/vault/VaultEntryListAdapter.kt"
        )
        val menu = sourceFile("app/src/main/res/menu/menu_vault.xml")
        val manager = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/vault/VaultManager.kt"
        )
        val viewOptions = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/vault/VaultViewOptionsController.kt"
        )
        val entryNameDialog = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/vault/VaultEntryNameDialogController.kt"
        )
        val importController = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/vault/VaultImportController.kt"
        )
        val operations = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/vault/VaultActivityOperations.kt"
        )
        val dialogs = activity + viewOptions + entryNameDialog + importController

        assertTrue("onLongClick = ::onEntryLongClicked" in activity)
        assertTrue("selectedEntryIds.isNotEmpty()" in activity)
        assertTrue("if (clearSelection())" in activity)
        assertTrue("confirmDeleteSelection()" in activity)
        assertTrue("file_delete_message_multiple_mixed_format" in activity)
        assertTrue("R.id.action_select_all" in activity)
        assertTrue("include_file_list_bottom_bar" in layout)
        assertTrue("@+id/overlayToolbar" in layout)
        assertFalse("@+id/menuButton" in row)
        assertFalse("@+id/thumbnailImage" in row)
        assertFalse("@+id/appIconBadgeImage" in row)
        assertFalse("@+id/badgeImage" in row)
        assertEquals(1, Regex("bg_badge_checkable_18dp").findAll(row).count())
        val primaryActionRow = layout
            .substringAfter("@+id/vaultPrimaryActionRow")
            .substringBefore("</LinearLayout>")
        assertTrue("@+id/importButton" in primaryActionRow)
        assertTrue("@+id/lockVaultButton" in primaryActionRow)
        assertEquals(2, Regex("android:layout_weight=\"1\"").findAll(primaryActionRow).count())
        assertTrue("@+id/iconLayout" in gridRow)
        assertTrue("@+id/nameText" in gridRow)
        assertTrue("GridLayoutManager(this, 1)" in activity)
        assertTrue("Settings.FILE_LIST_VIEW_TYPE.observe(this)" in activity)
        assertTrue("GridLayoutPolicy.spanCount(" in activity)
        assertTrue("ItemVaultEntryGridBinding" in adapter)
        assertTrue("FileViewType.GRID" in adapter)
        assertTrue("@+id/action_view_sort" in menu)
        assertFalse("@+id/action_view\"" in menu)
        assertTrue("@drawable/ic_sort_control_normal_24dp" in menu)
        assertTrue("app:showAsAction=\"always\"" in menu)
        assertTrue("@+id/topToolbarContainer" in layout)
        assertTrue("WindowInsetsCompat.Type.systemBars()" in activity)
        assertTrue("fun deleteEntries(vaultId: String, entryIds: Set<String>)" in manager)
        assertTrue("entry.parentId?.let(entryIdsToDelete::contains)" in manager)
        assertTrue(
            "import com.google.android.material.dialog.MaterialAlertDialogBuilder" in activity
        )
        assertEquals(6, Regex("MaterialAlertDialogBuilder\\(").findAll(dialogs).count())
        assertFalse("AlertDialog.Builder" in activity)
        assertTrue("importController.importPaths(currentParentId,paths)" in activity)
        assertTrue("operations.deleteEntries(selectedEntries)" in activity)
        assertTrue("operations.exportEncryptedBackup(uri)" in activity)
        assertTrue("manager.importPaths(vaultId,parentId,paths)" in operations)
        assertTrue("VaultFileTree::deleteRecursively" in operations)
        assertTrue("showDeleteOriginalsPrompt" in importController)
    }

    private fun sourceFile(path: String): String = File(root, path).readText()
    @Test
    fun vaultUsesUniversalSpeedDialInRequestedVisualOrder() {
        val layout = sourceFile("app/src/main/res/layout/activity_vault.xml")
        val menu = sourceFile("app/src/main/res/menu/menu_file_list_speed_dial.xml")
        val activity = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/vault/VaultActivity.kt"
        )

        assertTrue(layout.contains("@layout/include_file_list_speed_dial"))
        assertFalse(layout.contains("createFolderButton"))
        assertTrue(activity.contains("inflate(R.menu.menu_file_list_speed_dial)"))
        assertFalse(activity.contains("inflate(R.menu.menu_vault_speed_dial)"))
        assertTrue(activity.contains("R.id.action_create_file -> showCreateFileDialog()"))
        assertTrue(activity.contains("R.id.action_create_directory -> showCreateFolderDialog()"))
        assertTrue(activity.contains("R.id.action_create_vault -> startActivitySafe("))
        assertTrue(activity.contains("R.id.action_connect_cloud_drive -> startActivitySafe("))
        assertTrue(activity.contains("speedDialView.isVisible = !hasSelection"))
        assertTrue(activity.contains("SpeedDialViewOnBackPressedCallback"))

        val xmlOrder = Regex("android:id=\"@\\+id/([^\"]+)\"")
            .findAll(menu)
            .map { it.groupValues[1] }
            .toList()
        assertEquals(
            listOf(
                "action_connect_cloud_drive",
                "action_create_vault",
                "action_create_directory",
                "action_create_file"
            ),
            xmlOrder
        )
    }

    @Test
    fun vaultCreateDialogsInsetTheirNameFields() {
        val activity = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/vault/VaultActivity.kt"
        )
        val dialogController = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/vault/VaultEntryNameDialogController.kt"
        )

        assertTrue("entryNameDialogController.show(" in activity)
        assertTrue("fun createDialogNameInput()" in dialogController)
        assertTrue("fun show(" in dialogController)
        assertTrue("fun validateEntryName(" in dialogController)
        assertTrue("VaultEntryNamePolicy.hasConflict(" in dialogController)
        assertTrue("VaultEntryNameConflictException" in dialogController)
        assertTrue("R.string.file_name_error_already_exists" in dialogController)
        assertTrue("R.dimen.dialog_padding" in dialogController)
        assertEquals(1, Regex("\\.setView\\(inputContainer\\)").findAll(dialogController).count())
        assertFalse(".setView(edit)" in dialogController)
    }

    @Test
    fun vaultUsesFullViewOptionsAndSortingDialog() {
        val activity = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/vault/VaultActivity.kt"
        )
        val viewOptions = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/vault/VaultViewOptionsController.kt"
        )
        val menu = sourceFile("app/src/main/res/menu/menu_vault.xml")

        assertTrue("viewOptionsController.show(sortOptions)" in activity)
        assertTrue("R.string.file_list_action_view_sort" in viewOptions)
        assertTrue("R.id.action_view_sort" in activity)
        assertTrue("@+id/action_view_sort" in menu)
        assertTrue("app:showAsAction=\"always\"" in menu)
        assertTrue("Settings.FILE_LIST_SORT_OPTIONS.observe(this)" in activity)
        assertTrue("R.id.sort_by_type" in viewOptions)
        assertTrue("R.id.sort_by_size" in viewOptions)
        assertTrue("R.id.sort_by_last_modified" in viewOptions)
        assertTrue("R.id.sort_direction_descending" in viewOptions)
        assertTrue("showFoldersFirstCheckBox.isChecked" in viewOptions)
        assertTrue("sortVaultEntries(entries, sortOptions)" in activity)
        assertTrue("private fun updateEntrySubtitle()" in activity)
        assertTrue("R.plurals.file_list_subtitle_directory_count_format" in activity)
        assertTrue("R.plurals.file_list_subtitle_file_count_format" in activity)
        assertTrue("R.string.file_list_subtitle_separator" in activity)
        assertTrue("else -> getString(R.string.empty)" in activity)
        assertTrue("binding.toolbar.setSubtitle(R.string.loading)" in activity)
        assertTrue(
            "app:subtitleTextAppearance=\"@style/TextAppearance.AppCompat.Widget.ActionBar.Subtitle.Small\"" in
                sourceFile("app/src/main/res/layout/activity_vault.xml")
        )
        assertEquals(2, Regex("updateEntrySubtitle\\(\\)").findAll(activity).count())
    }


}
