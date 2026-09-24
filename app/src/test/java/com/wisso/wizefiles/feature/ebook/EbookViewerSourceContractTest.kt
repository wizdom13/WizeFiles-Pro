// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.ebook

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EbookViewerSourceContractTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `ebook route is private and active`() {
        val router = source("app/src/main/java/com/wisso/wizefiles/feature/internalviewer/InternalOpenIntents.kt")
        val manifest = source("app/src/main/AndroidManifest.xml")
        val externalActions = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListExternalActionController.kt"
        )
        val activity = source(
            "app/src/main/java/com/wisso/wizefiles/feature/ebook/EbookViewerActivity.kt"
        )
        assertTrue("EbookViewerIntents.create" in router)
        assertTrue("targetAfterExtraction(file.mimeType, file.path.name)" in externalActions)
        assertTrue("ArrayDeque<Iterator<Link>>" in activity)
        assertTrue("MAX_TOC_LINKS = 2_000" in activity)
        assertTrue("feature.ebook.EbookViewerActivity" in manifest)
        assertTrue("android:exported=\"false\"" in manifest)
    }

    @Test
    fun `readium is pinned offline and noninteractive`() {
        val gradle = source("app/build.gradle")
        val model = source("app/src/main/java/com/wisso/wizefiles/feature/ebook/EbookViewerViewModel.kt")
        val fragment = source("app/src/main/java/com/wisso/wizefiles/feature/ebook/EbookReaderFragment.kt")
        val offline = source("app/src/main/java/com/wisso/wizefiles/feature/ebook/OfflineHttpClient.kt")
        assertTrue("readium-shared:3.1.2" in gradle)
        assertTrue("readium-streamer:3.1.2" in gradle)
        assertTrue("readium-navigator:3.1.2" in gradle)
        assertTrue("allowUserInteraction = false" in model)
        assertTrue("OfflineHttpClient" in model)
        assertTrue("Try.failure" in offline)
        assertTrue("blockNetworkLoads = true" in fragment)
        assertTrue("allowFileAccess = false" in fragment)
        assertTrue("allowContentAccess = false" in fragment)
        assertFalse("registerJavascriptInterface" in fragment)
        assertFalse("DefaultHttpClient" in model)
    }

    @Test
    fun `mobi conversion excludes drm and is bounded`() {
        val cmake = source("app/CMakeLists.txt")
        val native = source("app/src/main/cpp/mobi_jni/mobi_jni.c")
        assertTrue("USE_ENCRYPTION OFF" in cmake)
        assertTrue("URL_HASH SHA256=78826d161c02ce93ff1cd62838b4d749df754f52185474b82e4093badf4689c1" in cmake)
        assertTrue("mobi_is_encrypted" in native)
        assertTrue("mobi_is_replica" in native)
        assertTrue("MAX_TOTAL_BYTES" in native)
        assertTrue("O_NOFOLLOW" in native)
        assertFalse("mobi_drm" in native)
    }

    private fun source(path: String): String = File(root, path).readText()
}

