// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkmImportSourceContractTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `APKM import verifies source and output without signing`() {
        val backend = source("app/src/main/java/com/wisso/wizefiles/feature/apksigning/ApkmImportBackend.kt")
        assertTrue("AndroidPackageContainerHint.APKM" in backend)
        assertTrue("AndroidPackageContainerHint.APKS" in backend)
        assertTrue("inputApks == outputApks" in backend)
        assertTrue("sourceReport.verified" in backend)
        assertFalse("ApkSigningRequest" in backend)
    }

    @Test
    fun `APKM action produces APKS through durable recovery`() {
        val browser = source("app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt") +
            source("app/src/main/java/com/wisso/wizefiles/feature/filebrowser/BrowserSelectionMenuConfigurator.kt") +
            source("app/src/main/java/com/wisso/wizefiles/feature/filebrowser/BrowserPackageSelectionActionHandler.kt")
        val service = source("app/src/main/java/com/wisso/wizefiles/feature/filejobs/FileOperationService.kt")
        val recovery = source("app/src/main/java/com/wisso/wizefiles/feature/transfer/TransferRecoveryManager.kt")
        assertTrue("endsWith(\".apkm\", ignoreCase = true)" in browser)
        assertTrue("ApkmImportActivity.createIntent" in browser)
        assertTrue("importApkm" in service)
        assertTrue("TransferOperationType.APKM_IMPORT" in recovery)
    }

    private fun source(path: String): String = File(root, path).readText()
}
