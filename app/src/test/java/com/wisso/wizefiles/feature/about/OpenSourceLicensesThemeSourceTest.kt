// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.about

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSourceLicensesThemeSourceTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `license html receives the active material palette`() {
        val activity = source(
            "app/src/main/java/com/wisso/wizefiles/feature/about/OpenSourceLicensesActivity.kt"
        )
        val html = source("app/src/main/res/raw/open_libs.html")

        listOf(
            "colorSurface",
            "colorSurfaceContainerLow",
            "colorSurfaceContainer",
            "colorOnSurface",
            "colorOnSurfaceVariant",
            "colorOutline",
            "colorSecondaryContainer",
            "colorOnSecondaryContainer",
            "colorPrimary"
        ).forEach { attribute -> assertTrue(attribute in activity) }
        assertTrue("MaterialColors.getColor" in activity)
        assertTrue("withMaterialTheme()" in activity)
        assertTrue("blockNetworkLoads = true" in activity)
        assertTrue("a { color: var(--link); }" in html)
    }

    private fun source(path: String): String = File(root, path).readText()
}
