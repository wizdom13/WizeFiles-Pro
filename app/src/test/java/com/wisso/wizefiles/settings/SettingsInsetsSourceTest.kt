package com.wisso.wizefiles.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsInsetsSourceTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `settings scrolling owner reserves the navigation bar inset`() {
        val fragment = source(
            "app/src/main/java/com/wisso/wizefiles/feature/settings/SettingsFragment.kt"
        )
        val preferences = source(
            "app/src/main/java/com/wisso/wizefiles/feature/settings/SettingsPreferenceFragment.kt"
        )
        val layout = source("app/src/main/res/layout/fragment_settings.xml")

        assertTrue("ViewCompat.setOnApplyWindowInsetsListener(root)" in fragment)
        assertTrue("WindowInsetsCompat.Type.navigationBars()" in fragment)
        assertFalse("root.updatePadding(" in fragment)
        assertTrue("initialScrollPaddingLeft + navigationBars.left" in fragment)
        assertTrue("initialScrollPaddingRight + navigationBars.right" in fragment)
        assertTrue("initialScrollPaddingBottom + navigationBars.bottom" in fragment)
        assertFalse("systemBars.top" in fragment)
        assertTrue("root.doOnAttach { ViewCompat.requestApplyInsets(it) }" in fragment)
        assertTrue("android:clipToPadding=\"false\"" in layout)
        assertFalse("android:fitsSystemWindows=\"true\"" in layout)
        assertFalse("setOnApplyWindowInsetsListener(this)" in preferences)
    }

    private fun source(path: String): String = File(root, path).readText()
}
