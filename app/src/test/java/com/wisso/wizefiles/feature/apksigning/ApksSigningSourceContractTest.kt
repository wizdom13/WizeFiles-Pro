// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApksSigningSourceContractTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `APKS preserves bundletool metadata and signs every internal APK`() {
        val backend = source("app/src/main/java/com/wisso/wizefiles/feature/apksigning/ApksArchiveSigningBackend.kt")
        assertTrue("toc.apkPaths == archiveApks" in backend)
        assertTrue("rebuiltToc.contentEquals(tocBytes)" in backend)
        assertTrue("inventory.apkEntries.forEachIndexed" in backend)
        assertTrue("Signed APKS failed complete split-set verification" in backend)
        assertTrue("wizeFilesMetadata" in backend)
    }

    @Test
    fun `APKS never offers detached v4 and credentials remain memory only`() {
        val activity = source("app/src/main/java/com/wisso/wizefiles/feature/apksigning/ApksSignVerifyActivity.kt") +
            source("app/src/main/java/com/wisso/wizefiles/feature/apksigning/SplitSetSignVerifyActivity.kt")
        val models = source("app/src/main/java/com/wisso/wizefiles/feature/apksigning/ApksSigningWorkflowModels.kt")
        assertFalse("scheme_v4" in activity)
        assertTrue("ApkSignatureScheme.V4 !in schemes" in models)
        assertTrue("isSaveEnabled = false" in activity)
        assertTrue("IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS" in activity)
        assertTrue("fill('\\u0000')" in activity)
    }

    @Test
    fun `single APKS actions and Transfer Center resume are wired`() {
        val browser = source("app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt") +
            source("app/src/main/java/com/wisso/wizefiles/feature/filebrowser/BrowserSelectionMenuConfigurator.kt") +
            source("app/src/main/java/com/wisso/wizefiles/feature/filebrowser/BrowserPackageSelectionActionHandler.kt")
        val service = source("app/src/main/java/com/wisso/wizefiles/feature/filejobs/FileOperationService.kt")
        val recovery = source("app/src/main/java/com/wisso/wizefiles/feature/transfer/TransferRecoveryManager.kt")
        assertTrue("setPackageVisibility(menu, singleFile, inRecycleBin, \".apks\"" in browser)
        assertTrue("ApksSignVerifyActivity.createSignIntent" in browser)
        assertTrue("ApksSignVerifyActivity.createVerifyIntent" in browser)
        assertTrue("signApks" in service)
        assertTrue("resumeApksSigning" in service)
        assertTrue("TransferOperationType.APKS_SIGN" in recovery)
    }

    @Test
    fun `APKS UI is Material and accepts both supported key source contracts`() {
        val activity = source("app/src/main/java/com/wisso/wizefiles/feature/apksigning/ApksSignVerifyActivity.kt") +
            source("app/src/main/java/com/wisso/wizefiles/feature/apksigning/SplitSetSignVerifyActivity.kt")
        assertTrue("MaterialToolbar" in activity)
        assertTrue("MaterialButton" in activity)
        assertTrue("TextInputLayout" in activity)
        assertTrue("MaterialCardView" in activity)
        assertTrue("AabSigningKeySource.KEY_STORE" in activity)
        assertTrue("AabSigningKeySource.PKCS8_CERTIFICATE" in activity)
        assertFalse("android.widget.Button" in activity)
        assertFalse("android.widget.EditText" in activity)
        assertFalse("android.widget.Spinner" in activity)
    }

    private fun source(path: String): String = File(root, path).readText()
}
