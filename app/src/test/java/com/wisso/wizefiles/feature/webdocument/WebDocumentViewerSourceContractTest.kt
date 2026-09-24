// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.webdocument

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDocumentViewerSourceContractTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `saved web formats use a private focused route`() {
        val router = source("app/src/main/java/com/wisso/wizefiles/feature/internalviewer/InternalOpenIntents.kt")
        val policy = source("app/src/main/java/com/wisso/wizefiles/feature/internalviewer/InternalOpenPolicy.kt")
        val externalActions = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListExternalActionController.kt"
        )
        val manifest = source("app/src/main/AndroidManifest.xml")
        assertTrue("WebDocumentViewerIntents.create" in router)
        assertTrue("FileFormat.Family.WEB_DOCUMENT" in policy)
        assertTrue("Target.WEB_DOCUMENT_VIEWER" in externalActions)
        assertTrue("legacyPath?.isArchivePath == true" in externalActions)
        assertTrue("feature.webdocument.WebDocumentViewerActivity" in manifest)
        assertTrue("android:exported=\"false\"" in manifest)
    }

    @Test
    fun `webview is offline scriptless and has no native bridge`() {
        val activity = source("app/src/main/java/com/wisso/wizefiles/feature/webdocument/WebDocumentViewerActivity.kt")
        assertTrue("javaScriptEnabled = false" in activity)
        assertTrue("blockNetworkLoads = true" in activity)
        assertTrue("allowFileAccess = false" in activity)
        assertTrue("allowContentAccess = false" in activity)
        assertTrue("script-src 'none'" in activity)
        assertTrue("ByteArrayInputStream(ByteArray(0))" in activity)
        assertFalse("addJavascriptInterface" in activity)
        assertFalse("registerJavascriptInterface" in activity)
    }

    @Test
    fun `archive and mime bundles are bounded`() {
        val archive = source("app/src/main/java/com/wisso/wizefiles/feature/webdocument/SafeWebArchiveExtractor.kt")
        val mhtml = source("app/src/main/java/com/wisso/wizefiles/feature/webdocument/MhtmlBundleParser.kt")
        assertTrue("MAX_ENTRIES = 5_000" in archive)
        assertTrue("MAX_TOTAL_BYTES = 256L * 1024 * 1024" in archive)
        assertTrue("LinkOption.NOFOLLOW_LINKS" in archive)
        assertTrue("MAX_SOURCE_BYTES = 16L * 1024 * 1024" in mhtml)
        assertTrue("MAX_PARTS = 2_000" in mhtml)
    }

    private fun source(path: String): String = File(root, path).readText()
}

