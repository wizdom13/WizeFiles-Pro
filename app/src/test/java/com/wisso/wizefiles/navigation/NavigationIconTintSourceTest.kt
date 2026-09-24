// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.navigation

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationIconTintSourceTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `navigation drawer icons use the theme primary color`() {
        val adapter = File(
            root,
            "app/src/main/java/com/wisso/wizefiles/feature/navigation/NavigationListAdapter.kt"
        ).readText()

        assertTrue("colorPrimary" in adapter)
        assertTrue("NavigationView_itemIconTint" !in adapter)
    }
}
