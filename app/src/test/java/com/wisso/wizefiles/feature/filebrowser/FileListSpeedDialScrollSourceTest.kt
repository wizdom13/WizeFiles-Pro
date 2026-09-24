package com.wisso.wizefiles.feature.filebrowser

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FileListSpeedDialScrollSourceTest {
    @Test
    fun `file list fragment animates speed dial on scroll direction`() {
        val source = File("src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt").readText()

        assertTrue(source.contains("binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener()"))
        assertTrue(source.contains("dy > 0 -> hideSpeedDialForScroll()"))
        assertTrue(source.contains("dy < 0 -> showSpeedDialForScroll()"))
        assertTrue(source.contains("private fun hideSpeedDialForScroll()"))
        assertTrue(source.contains("private fun showSpeedDialForScroll()"))
        assertTrue(source.contains("private fun updateSpeedDialVisibility()"))
        assertTrue(source.contains("val shouldShow = !isSpeedDialHiddenByScroll && !isBottomPanelVisible"))
        assertTrue(source.contains(".translationY(if (shouldShow) 0f else hiddenTranslationY)"))
        assertTrue(source.contains(".alpha(if (shouldShow) 1f else 0f)"))
        assertTrue(source.contains("speedDialView.close()"))
    }

    @Test
    fun `speed dial opens cloud drive setup and keeps the requested visual order`() {
        val source = File(
            "src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt"
        ).readText()
        val menu = File("src/main/res/menu/menu_file_list_speed_dial.xml").readText()

        assertTrue(source.contains("R.id.action_connect_cloud_drive -> startActivitySafe("))
        assertTrue(source.contains("EditRcloneStorageActivity::class.createIntent()"))
        assertTrue(source.contains(".putArgs(EditRcloneStorageFragment.Args())"))

        val xmlOrder = Regex("android:id=\"@\\+id/([^\"]+)\"")
            .findAll(menu)
            .map { it.groupValues[1] }
            .toList()
        assertTrue(
            xmlOrder == listOf(
                "action_connect_cloud_drive",
                "action_create_vault",
                "action_create_directory",
                "action_create_file"
            )
        )
        assertTrue(menu.contains("@string/storage_add_storage_rclone"))
        assertTrue(menu.contains("@drawable/ic_cloud_white_24dp"))
    }

    @Test
    fun `speed dial uses a dim scrim and material 3 container label colors`() {
        val source = File("src/main/java/com/wisso/wizefiles/ui/ThemedSpeedDialView.kt").readText()

        assertTrue(source.contains("Color.BLACK.asColor()"))
        assertTrue(source.contains("SPEED_DIAL_SCRIM_ALPHA = 0.32f"))
        assertTrue(source.contains("R.attr.colorPrimaryContainer"))
        assertTrue(source.contains("R.attr.colorOnPrimaryContainer"))
        assertTrue(source.contains("SPEED_DIAL_LABEL_HEIGHT_DP = 40"))
        assertTrue(source.contains("SPEED_DIAL_LABEL_MAX_WIDTH_DP = 240"))
        assertTrue(source.contains("SPEED_DIAL_LABEL_HORIZONTAL_PADDING_DP = 12"))
        assertTrue(source.contains("SPEED_DIAL_LABEL_TEXT_SIZE_SP = 18f"))
        assertTrue(source.contains("minimumHeight = labelHeight"))
        assertTrue(source.contains("maxWidth = labelMaxWidth"))
        assertTrue(source.contains("radius = labelHeight / 2f"))
        assertTrue(source.contains("gravity = Gravity.CENTER"))
        assertTrue(source.contains("setPadding(horizontalPadding, 0, horizontalPadding, 0)"))
    }
    @Test
    fun `speed dial opens secure folder creation`() {
        val source = File(
            "src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt"
        ).readText()
        val menu = File("src/main/res/menu/menu_file_list_speed_dial.xml").readText()

        assertTrue(source.contains("R.id.action_create_vault -> startActivitySafe("))
        assertTrue(source.contains("AddVaultDialogActivity::class.createIntent()"))
        assertTrue(menu.contains("action_create_vault"))
        assertTrue(menu.contains("@string/vault_create_folder"))
        assertTrue(menu.contains("@drawable/ic_lock_white_24dp"))
        assertTrue(
            menu.indexOf("action_create_vault") < menu.indexOf("action_create_directory")
        )
    }


}
