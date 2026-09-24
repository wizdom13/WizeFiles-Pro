// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AabSigningSourceContractTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `AAB uses JAR upload signatures and never APK scheme controls`() {
        val backend = source(
            "app/src/main/java/com/wisso/wizefiles/feature/apksigning/JarAabSigningBackend.kt"
        )
        val activity = source(
            "app/src/main/java/com/wisso/wizefiles/feature/apksigning/AabSignVerifyActivity.kt"
        )
        assertTrue("SHA-256-Digest-Manifest" in backend)
        assertTrue("CMSSignedDataGenerator" in backend)
        assertTrue("AndroidPackageContainerHint.AAB" in backend)
        assertTrue("val verification = verify" in backend)
        assertFalse("ApkSignatureScheme" in activity)
        assertFalse("scheme_v1" in activity)
        assertFalse("scheme_v4" in activity)
    }

    @Test
    fun `AAB credentials stay memory only and resume through Transfer Center`() {
        val activity = source(
            "app/src/main/java/com/wisso/wizefiles/feature/apksigning/AabSignVerifyActivity.kt"
        ) + source(
            "app/src/main/java/com/wisso/wizefiles/feature/apksigning/PackageSigningActivity.kt"
        )
        val service = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filejobs/FileOperationService.kt"
        )
        val recovery = source(
            "app/src/main/java/com/wisso/wizefiles/feature/transfer/TransferRecoveryManager.kt"
        )
        assertTrue("isSaveEnabled = false" in activity)
        assertTrue("IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS" in activity)
        assertTrue("fill('\\u0000')" in activity)
        assertTrue("signAab" in service)
        assertTrue("resumeAabSigning" in service)
        assertTrue("TransferOperationType.AAB_SIGN" in recovery)
        assertTrue("SIGNING_SECRET_REQUIRED" in recovery)
    }

    @Test
    fun `AAB screen follows Material app controls and both key source contracts`() {
        val activity = source(
            "app/src/main/java/com/wisso/wizefiles/feature/apksigning/AabSignVerifyActivity.kt"
        )
        val keyService = source(
            "app/src/main/java/com/wisso/wizefiles/feature/apksigning/AabSigningKeyMaterialService.kt"
        )
        assertTrue("MaterialToolbar" in activity)
        assertTrue("MaterialButton" in activity)
        assertTrue("TextInputLayout" in activity)
        assertTrue("MaterialCardView" in activity)
        assertFalse("android.widget.Button" in activity)
        assertFalse("android.widget.EditText" in activity)
        assertFalse("android.widget.Spinner" in activity)
        assertTrue("AabSigningKeySource.KEY_STORE" in keyService)
        assertTrue("AabSigningKeySource.PKCS8_CERTIFICATE" in keyService)
        assertTrue("requireKeyMatchesCertificate" in keyService)
    }

    @Test
    fun `single AAB actions are wired without broadening APK actions`() {
        val browser = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt"
        ) + source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/BrowserSelectionMenuConfigurator.kt"
        ) + source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/BrowserPackageSelectionActionHandler.kt"
        )
        assertTrue("action_sign_aab" in browser)
        assertTrue("action_verify_aab" in browser)
        assertTrue("setPackageVisibility(menu, singleFile, inRecycleBin, \".aab\"" in browser)
        assertTrue("AabSignVerifyActivity.createSignIntent" in browser)
        assertTrue("AabSignVerifyActivity.createVerifyIntent" in browser)
    }

    private fun source(path: String): String = File(root, path).readText()
}
