package com.wisso.wizefiles.navigation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationDrawerHeaderSourceTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `drawer starts with a lightweight adaptive brand header`() {
        val fragment = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/navigation/NavigationFragment.kt"
        )
        val adapter = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/navigation/NavigationHeaderAdapter.kt"
        )
        val recyclerView = sourceFile(
            "app/src/main/java/com/wisso/wizefiles/feature/navigation/NavigationRecyclerView.kt"
        )
        val layout = sourceFile("app/src/main/res/layout/item_navigation_header.xml")
        val dimensions = sourceFile("app/src/main/res/values/dimens.xml")
        val shape = sourceFile("app/src/main/res/values/navigation_header_styles.xml")
        val secondaryShape = sourceFile(
            "app/src/main/res/drawable/bg_navigation_header_secondary_shape.xml"
        )
        val tertiaryShape = sourceFile(
            "app/src/main/res/drawable/bg_navigation_header_tertiary_shape.xml"
        )

        assertTrue("ConcatAdapter.Config.Builder()" in fragment)
        assertTrue("StableIdMode.ISOLATED_STABLE_IDS" in fragment)
        assertTrue("NavigationHeaderAdapter()" in fragment)
        assertTrue("ItemNavigationHeaderBinding" in adapter)
        assertTrue("override fun getItemCount(): Int = 1" in adapter)
        assertTrue("topMargin = -topExtension" in adapter)
        assertTrue("contentHeight + topExtension" in adapter)
        assertTrue("@dimen/navigation_header_height" in layout)
        assertTrue("<dimen name=\"navigation_header_height\">112dp</dimen>" in dimensions)
        assertTrue("android:layout_gravity=\"bottom\"" in layout)
        assertFalse(
            "android:layout_marginStart=\"@dimen/screen_edge_margin_minus_8dp\"" in layout
        )
        assertFalse(
            "android:layout_marginEnd=\"@dimen/screen_edge_margin_minus_8dp\"" in layout
        )
        assertTrue("@style/ShapeAppearance.WizeFiles.NavigationHeader" in layout)
        assertTrue(
            "<style name=\"ShapeAppearance.WizeFiles.NavigationHeader\" parent=\"\">" in shape
        )
        assertTrue("<item name=\"cornerSizeTopLeft\">0dp</item>" in shape)
        assertTrue("<item name=\"cornerSizeTopRight\">0dp</item>" in shape)
        assertTrue("<item name=\"cornerSizeBottomLeft\">28dp</item>" in shape)
        assertTrue("<item name=\"cornerSizeBottomRight\">28dp</item>" in shape)
        assertTrue("?attr/colorPrimaryContainer" in layout)
        assertTrue("colorPrimaryContainer" in recyclerView)
        assertTrue("!isHeaderCoveringStatusBar()" in recyclerView)
        assertTrue("findViewHolderForAdapterPosition(0)" in recyclerView)
        assertTrue("header.top < insetTop && header.bottom > insetTop" in recyclerView)
        assertTrue("?attr/colorSecondaryContainer" in secondaryShape)
        assertTrue("?attr/colorTertiaryContainer" in tertiaryShape)
        assertTrue("@drawable/bg_navigation_header_secondary_shape" in layout)
        assertTrue("@drawable/bg_navigation_header_tertiary_shape" in layout)
        assertTrue("android:alpha=\"0.10\"" in layout)
        assertTrue("android:alpha=\"0.14\"" in layout)
        assertTrue("?attr/colorOnPrimaryContainer" in layout)
        assertTrue("@drawable/ic_wizefiles_mark" in layout)
        assertTrue("@string/navigation_drawer_header_title" in layout)
        assertTrue(
            "translatable=\"false\">WizeFiles</string>" in
                sourceFile("app/src/main/res/values/navigation_drawer_strings.xml")
        )
        assertTrue("android:contentDescription=\"@null\"" in layout)
    }

    private fun sourceFile(path: String): String = File(root, path).readText()
}
