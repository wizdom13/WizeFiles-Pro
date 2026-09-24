package com.wisso.wizefiles.feature.filebrowser

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserLocationAndPasteSourceTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `navigation root icon is rendered before the breadcrumb label`() {
        val navigationRoot = source(
            "app/src/main/java/com/wisso/wizefiles/feature/navigation/NavigationRoot.kt"
        )
        val data = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/BreadcrumbData.kt"
        )
        val liveData = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/BreadcrumbLiveData.kt"
        )
        val layout = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/BreadcrumbLayout.kt"
        )
        val item = source("app/src/main/res/layout/item_breadcrumb.xml")
        val appBar = source("app/src/main/res/layout/include_file_list_app_bar.xml")
        val dimens = source("app/src/main/res/values/dimens.xml")

        assertTrue("val iconRes: Int" in navigationRoot)
        assertTrue("val iconResIds: List<Int?>" in data)
        assertTrue("iconResIds.add(navigationRoot.iconRes)" in liveData)
        assertTrue("binding.locationImage.isVisible = iconRes != null" in layout)
        assertTrue("com.google.android.material.R.attr.colorPrimary" in layout)
        assertTrue("binding.locationImage.imageTintList = locationIconColor" in layout)
        assertTrue(item.indexOf("@+id/locationImage") < item.indexOf("@+id/text"))
        assertTrue("android:importantForAccessibility=\"no\"" in item)
        assertTrue(
            "android:paddingStart=\"@dimen/file_list_breadcrumb_padding_start\"" in appBar
        )
        assertTrue("<dimen name=\"file_list_breadcrumb_padding_start\">10dp</dimen>" in dimens)
    }

    @Test
    fun `paste menu actions are hidden instead of disabled`() {
        val fragment = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt"
        )
        val bottomPanel = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListBottomPanelController.kt"
        )
        val mainMenu = source("app/src/main/res/menu/menu_file_list.xml")
        val backgroundMenu = source(
            "app/src/main/res/menu/menu_file_list_context_background.xml"
        )
        val updatePaste = fragment.substringAfter("private fun updatePasteMenuItem")
            .substringBefore("private fun updateBottomToolbar")
        val backgroundPaste = fragment.substringAfter("private fun showPaneContextMenu")
            .substringBefore("private fun showPathContextMenu")

        assertTrue(
            "pasteItem.isVisible = isBrowserCommandAvailable(BrowserCommand.PASTE)" in updatePaste
        )
        assertFalse("pasteItem.isEnabled" in updatePaste)
        assertTrue("isVisible = pasteAvailable" in backgroundPaste)
        assertFalse("action_paste).isEnabled" in backgroundPaste)
        assertTrue("android:visible=\"false\"" in mainMenu)
        assertTrue("android:visible=\"false\"" in backgroundMenu)
        assertTrue("isVisible = state.pasteAvailable" in bottomPanel)
    }

    private fun source(path: String): String = File(root, path).readText()
}
