// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import com.wisso.wizefiles.ui.resolveBottomBarMargin
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileListOperationPanelSourceTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `operation panel is compact floating and shares the FAB container color`() {
        val layout = sourceFile("app/src/main/res/layout/include_file_list_bottom_bar.xml")
        val dimensions = sourceFile("app/src/main/res/values/dimens.xml")
        val speedDial = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/ui/ThemedSpeedDialView.kt"
        )

        assertTrue("android:layout_height=\"@dimen/file_list_operation_bar_height\"" in layout)
        assertTrue("<dimen name=\"file_list_operation_bar_height\">48dp</dimen>" in dimensions)
        assertTrue("android:layout_marginStart=\"@dimen/file_list_operation_bar_horizontal_margin\"" in layout)
        assertTrue("android:layout_marginEnd=\"@dimen/file_list_operation_bar_horizontal_margin\"" in layout)
        assertTrue("app:barCornerRadius=\"@dimen/file_list_operation_bar_corner_radius\"" in layout)
        assertTrue("app:bottomInsetAsMargin=\"true\"" in layout)
        assertTrue("android:background=\"?colorSecondaryContainer\"" in layout)
        assertTrue("colorSecondaryContainer" in speedDial)
    }

    @Test
    fun `navigation inset becomes an external gap while the panel overlays content`() {
        assertEquals(32, resolveBottomBarMargin(8, 24, 0))
        assertEquals(248, resolveBottomBarMargin(8, 24, 240))

        val persistentBar = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/ui/PersistentBarLayout.kt"
        )
        val updateInsets = persistentBar.substringAfter(
            "private fun updateContentViewsWindowInsets()"
        ).substringBefore("private fun WindowInsetsCompat.withSystemBarsInsets")
        val measureContent = persistentBar.substringAfter(
            "private fun measureContentViews()"
        ).substringBefore("override fun onLayout")

        assertTrue("if (isTopBarView(child))" in updateInsets)
        assertTrue("if (isTopBarView(child))" in measureContent)
        assertTrue("contentSystemBarsInsets.bottom" in updateInsets)
        assertTrue("height - child.height - childLayoutParams.bottomMargin" in persistentBar)
    }

    @Test
    fun `operation panel enters and exits through the physical left edge`() {
        val actionMode = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/ui/PersistentBarLayoutToolbarActionMode.kt"
        )

        assertTrue("persistentBarLayout.showBar(bar, false)" in actionMode)
        assertTrue("bar.translationX = -offscreenDistance" in actionMode)
        assertTrue(".translationX(-offscreenDistance)" in actionMode)
        assertTrue("persistentBarLayout.hideBar(bar, false)" in actionMode)
    }

    private fun sourceFile(path: String): String = File(root, path).readText()
}
