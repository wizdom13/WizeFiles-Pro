// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XapkSigningSourceContractTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `XAPK preserves non-APK payloads and signs every APK`() {
        val backend = source("app/src/main/java/com/wisso/wizefiles/feature/apksigning/XapkArchiveSigningBackend.kt")
        assertTrue("manifestApks == archiveApks" in backend)
        assertTrue("inventory.apkEntries.forEachIndexed" in backend)
        assertTrue("rebuilt.contentEquals(manifestBytes)" in backend)
        assertTrue("originalNonApks == rebuiltNonApks" in backend)
        assertTrue("Signed XAPK failed complete package-set verification" in backend)
    }

    @Test
    fun `XAPK uses embedded schemes and memory-only credentials`() {
        val activity = source("app/src/main/java/com/wisso/wizefiles/feature/apksigning/XapkSignVerifyActivity.kt") +
            source("app/src/main/java/com/wisso/wizefiles/feature/apksigning/SplitSetSignVerifyActivity.kt")
        val models = source("app/src/main/java/com/wisso/wizefiles/feature/apksigning/XapkSigningWorkflowModels.kt")
        assertFalse("scheme_v4" in activity)
        assertTrue("ApkSignatureScheme.V4 !in schemes" in models)
        assertTrue("isSaveEnabled = false" in activity)
        assertTrue("IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS" in activity)
        assertTrue("fill('\\u0000')" in activity)
        assertTrue("AabSigningKeySource.PKCS8_CERTIFICATE" in activity)
    }

    @Test
    fun `XAPK file actions and recovery are wired`() {
        val browser = source("app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt") +
            source("app/src/main/java/com/wisso/wizefiles/feature/filebrowser/BrowserSelectionMenuConfigurator.kt") +
            source("app/src/main/java/com/wisso/wizefiles/feature/filebrowser/BrowserPackageSelectionActionHandler.kt")
        val service = source("app/src/main/java/com/wisso/wizefiles/feature/filejobs/FileOperationService.kt")
        val recovery = source("app/src/main/java/com/wisso/wizefiles/feature/transfer/TransferRecoveryManager.kt")
        assertTrue("setPackageVisibility(menu, singleFile, inRecycleBin, \".xapk\"" in browser)
        assertTrue("XapkSignVerifyActivity.createSignIntent" in browser)
        assertTrue("XapkSignVerifyActivity.createVerifyIntent" in browser)
        assertTrue("signXapk" in service)
        assertTrue("resumeXapkSigning" in service)
        assertTrue("TransferOperationType.XAPK_SIGN" in recovery)
    }

    private fun source(path: String): String = File(root, path).readText()
}
