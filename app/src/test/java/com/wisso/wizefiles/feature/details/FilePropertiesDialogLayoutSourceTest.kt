package com.wisso.wizefiles.feature.details

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilePropertiesDialogLayoutSourceTest {

    @Test
    fun detailsDialog_usesStackedSectionsInsteadOfTabs() {
        val dialogSource = sourceFile(
            "src/main/java/com/wisso/wizefiles/feature/details/FilePropertiesDialogFragment.kt"
        )
        val dialogLayout = sourceFile("src/main/res/layout/dialog_file_properties.xml")
        val styles = sourceFile("src/main/res/values/styles.xml")

        assertFalse(dialogSource.contains("TabLayoutMediator"))
        assertFalse(dialogLayout.contains("com.google.android.material.tabs.TabLayout"))
        assertFalse(dialogLayout.contains("androidx.viewpager2.widget.ViewPager2"))
        assertTrue(dialogLayout.contains("@+id/sectionsContainer"))
        assertTrue(dialogLayout.contains("@+id/titleText"))
        assertTrue(dialogLayout.contains("@+id/okButton"))
        assertTrue(dialogSource.contains("ItemFilePropertiesSectionBinding"))
        assertTrue(dialogSource.contains("ThemeOverlay_WizeFiles_FileProperties_Dialog"))
        assertTrue(dialogLayout.contains("?colorSurfaceContainerLow"))
        assertTrue(
            dialogLayout.contains("<androidx.core.widget.NestedScrollView") &&
                dialogLayout.contains("android:background=\"?colorSurfaceContainerLow\"")
        )
        assertFalse(
            dialogLayout.contains(
                "<androidx.core.widget.NestedScrollView\n" +
                    "        android:layout_width=\"match_parent\"\n" +
                    "        android:layout_height=\"wrap_content\""
            )
        )
        assertTrue(dialogLayout.contains("android:layout_height=\"0dp\""))
        assertTrue(dialogLayout.contains("android:layout_weight=\"1\""))
        assertTrue(styles.contains("name=\"ThemeOverlay.WizeFiles.FileProperties.Dialog\""))
        assertTrue(styles.contains("<item name=\"backgroundTint\">?colorSurfaceContainerLow</item>"))
        assertTrue(dialogSource.contains("binding.titleText.text"))
        assertTrue(dialogSource.contains("binding.okButton.setOnClickListener"))
        assertFalse(dialogSource.contains(".setPositiveButton("))
    }

    private fun sourceFile(path: String): String {
        val file = listOf(File(path), File("app/$path")).firstOrNull { it.exists() }
            ?: error("Unable to locate source file for $path")
        return file.readText()
    }
}
