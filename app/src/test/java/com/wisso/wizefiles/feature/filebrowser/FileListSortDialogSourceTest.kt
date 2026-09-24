package com.wisso.wizefiles.feature.filebrowser

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileListSortDialogSourceTest {

    @Test
    fun fileListSortLayoutIncludesAllRequiredOptions() {
        val layout = layoutFile("dialog_file_list_sort.xml").readText()

        assertTrue(layout.contains("@+id/view_type_group"))
        assertTrue(layout.contains("@+id/view_type_list"))
        assertTrue(layout.contains("@+id/view_type_grid"))
        assertTrue(layout.contains("@+id/grid_columns_button"))
        assertTrue(layout.contains("@string/file_list_grid_columns_auto"))
        assertTrue(layout.contains("@+id/sort_by_name"))
        assertTrue(layout.contains("@+id/sort_by_type"))
        assertTrue(layout.contains("@+id/sort_by_size"))
        assertTrue(layout.contains("@+id/sort_by_last_modified"))
        assertTrue(layout.contains("@+id/sort_direction_ascending"))
        assertTrue(layout.contains("@+id/sort_direction_descending"))
        assertTrue(layout.contains("@+id/show_folders_first"))
        assertTrue(layout.contains("@+id/only_for_this_folder"))
    }

    @Test
    fun gridColumnOverrideIsVisibleOnlyForGridView() {
        val controller = sourceFile(
            "src/main/java/com/wisso/wizefiles/feature/filebrowser/GridColumnOverrideUi.kt"
        ).readText()

        assertTrue(
            controller.contains("button.isVisible = checkedId == R.id.view_type_grid")
        )
        assertTrue(controller.contains("GridColumnOverrides.MANUAL_RANGE"))
        assertTrue(controller.contains("R.string.file_list_grid_columns_auto"))
    }

    @Test
    fun fileListMenuUsesDialogEntryWithoutSortSubmenu() {
        val menu = menuFile("menu_file_list.xml").readText()

        assertTrue(menu.contains("android:id=\"@+id/action_view_sort\""))
        assertFalse(menu.contains("@+id/action_sort_by_name"))
        assertFalse(menu.contains("@+id/action_view_sort_path_specific"))
    }
}

private fun layoutFile(name: String): File = listOf(
    File("src/main/res/layout/$name"),
    File("app/src/main/res/layout/$name")
).firstOrNull { it.exists() } ?: error("Layout $name not found")

private fun sourceFile(path: String): File = listOf(
    File(path),
    File("app/$path")
).firstOrNull { it.exists() } ?: error("Source $path not found")

private fun menuFile(name: String): File = listOf(
    File("src/main/res/menu/$name"),
    File("app/src/main/res/menu/$name")
).firstOrNull { it.exists() } ?: error("Menu $name not found")
