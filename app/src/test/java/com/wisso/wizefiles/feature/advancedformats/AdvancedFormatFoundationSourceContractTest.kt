// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.advancedformats

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedFormatFoundationSourceContractTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `advanced destinations remain declarations rather than active routes`() {
        val browser = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt"
        )
        val intents = source(
            "app/src/main/java/com/wisso/wizefiles/feature/internalviewer/InternalOpenIntents.kt"
        )
        val pager = source(
            "app/src/main/java/com/wisso/wizefiles/feature/mediapreview/MediaPreviewPagerAdapter.kt"
        )
        val sessionStore = source(
            "app/src/main/java/com/wisso/wizefiles/feature/mediapreview/MediaPreviewSessionStore.kt"
        )

        assertFalse("FileFormatDetector" in browser)
        assertFalse("plannedTargetFor" in intents)
        assertTrue("InternalOpenPolicy.Target.EBOOK_VIEWER" in intents)
        assertTrue("InternalOpenPolicy.Target.WEB_DOCUMENT_VIEWER" in intents)
        assertTrue("InternalOpenPolicy.Target.CONTAINER_BROWSER" in intents)
        listOf(
            "InternalOpenPolicy.Target.EBOOK_VIEWER",
            "InternalOpenPolicy.Target.WEB_DOCUMENT_VIEWER",
            "InternalOpenPolicy.Target.CONTAINER_BROWSER"
        ).forEach { plannedTarget ->
            assertTrue(plannedTarget in pager)
            assertTrue(plannedTarget in sessionStore)
        }
    }

    @Test
    fun `native provenance matches pinned libarchive build`() {
        val registry = JSONObject(source("app/src/main/cpp/native-dependencies.json"))
        val components = registry.getJSONArray("components")
        val libarchive = (0 until components.length())
            .map { components.getJSONObject(it) }
            .first { it.getString("name") == "libarchive" }
        val cmake = source("app/CMakeLists.txt")
        val gradle = source("app/build.gradle")

        assertEquals("3.8.9", libarchive.getString("version"))
        assertTrue(libarchive.getString("sha256") in cmake)
        assertTrue("ndkVersion = '29.0.14206865'" in gradle)
        assertEquals(16_384, registry.getJSONObject("build").getInt("minimumLoadAlignmentBytes"))
        assertEquals(
            listOf("arm64-v8a", "x86_64"),
            registry.getJSONObject("build").getJSONArray("requiredAlignedAbis").let { abis ->
                (0 until abis.length()).map(abis::getString)
            }
        )
    }

    @Test
    fun `ci verifies packaged elf page alignment`() {
        val workflow = source(".github/workflows/android.yml")
        val script = source("scripts/verify-native-page-size.sh")

        assertTrue("verify-native-page-size.sh" in workflow)
        assertTrue("llvm-readelf" in script)
        assertTrue("arm64-v8a|x86_64" in script)
        assertTrue("0x4000" in script)
        assertTrue("zipalign" in script)
        assertTrue("-P 16" in script)
        assertTrue("app-debug.apk" in workflow)
    }

    private fun source(path: String): String = File(root, path).readText()
}
