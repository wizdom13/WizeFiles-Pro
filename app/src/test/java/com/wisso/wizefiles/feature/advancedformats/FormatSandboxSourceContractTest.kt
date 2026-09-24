// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.advancedformats

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatSandboxSourceContractTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `sandbox is private isolated and permissionless`() {
        val manifest = source("app/src/main/AndroidManifest.xml")
        val service = source(
            "app/src/main/java/com/wisso/wizefiles/feature/advancedformats/sandbox/" +
                "FormatSandboxService.kt"
        )

        assertTrue("feature.advancedformats.sandbox.FormatSandboxService" in manifest)
        assertTrue("android:exported=\"false\"" in manifest)
        assertTrue("android:isolatedProcess=\"true\"" in manifest)
        assertFalse("android:permission" in sandboxManifestDeclaration(manifest))
        assertFalse("java.nio.file.Path" in service)
        assertFalse("contentResolver" in service)
    }

    @Test
    fun `ipc passes only bounded values and caller owned descriptors`() {
        val serviceAidl = source(
            "app/src/main/aidl/com/wisso/wizefiles/feature/advancedformats/sandbox/" +
                "IFormatSandboxService.aidl"
        )
        val models = source(
            "app/src/main/java/com/wisso/wizefiles/feature/advancedformats/sandbox/" +
                "FormatSandboxModels.kt"
        )

        assertTrue("ParcelFileDescriptor input" in serviceAidl)
        assertTrue("ParcelFileDescriptor output" in serviceAidl)
        assertTrue("void cancel(long requestId)" in serviceAidl)
        assertTrue("MAX_TRANSFER_BYTES" in models)
        assertTrue("MAX_TIMEOUT_MILLIS" in models)
        assertFalse("String path" in serviceAidl)
        assertFalse("password" in serviceAidl.lowercase())
    }

    @Test
    fun `client observes binder death and reconnects`() {
        val client = source(
            "app/src/main/java/com/wisso/wizefiles/feature/advancedformats/sandbox/" +
                "FormatSandboxClient.kt"
        )

        assertTrue("IBinder.DeathRecipient" in client)
        assertTrue("linkToDeath" in client)
        assertTrue("onBindingDied" in client)
        assertTrue("scheduleReconnect" in client)
    }

    private fun sandboxManifestDeclaration(manifest: String): String = manifest
        .substringAfter("feature.advancedformats.sandbox.FormatSandboxService")
        .substringBefore("/>")

    private fun source(path: String): String = File(root, path).readText()
}
