// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.hiddenapi

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenApiBypassInitializationTest {

    @Test
    fun disableHiddenApiChecksForSdkLoadsLibraryOnlyOnAndroidPAndAbove() {
        val loadedLibraries = mutableListOf<String>()

        HiddenApi.disableHiddenApiChecksForSdk(27) { loadedLibraries += it }
        HiddenApi.disableHiddenApiChecksForSdk(28) { loadedLibraries += it }

        assertEquals(listOf("hiddenapi"), loadedLibraries)
    }

    @Test(expected = UnsatisfiedLinkError::class)
    fun disableHiddenApiChecksForSdkPropagatesLoaderFailure() {
        HiddenApi.disableHiddenApiChecksForSdk(28) {
            throw UnsatisfiedLinkError("boom")
        }
    }

    @Test
    fun appStartupGuardsHiddenApiBypassBehindGateAndHandlesFailure() {
        val appInitializers =
            sourceFile("src/main/java/com/wisso/wizefiles/core/app/AppInitializerRegistry.kt")
        val appBuildGradle = sourceFile("build.gradle")

        assertTrue(appBuildGradle.contains("ENABLE_HIDDEN_API_BYPASS"))
        assertTrue(appInitializers.contains("if (!BuildConfig.ENABLE_HIDDEN_API_BYPASS)"))
        assertTrue(appInitializers.contains("runCatching"))
        assertTrue(appInitializers.contains("Failed to initialize hidden API bypass"))
    }


    @Test
    fun hiddenapiJniReturnsAfterNewObjectArrayFailureBeforeCallVoidMethod() {
        val source = sourceFile("src/main/jni/hiddenapi.c")
        val branchStart = source.indexOf("if (!signaturePrefixes)")
        assertTrue("Expected !signaturePrefixes branch", branchStart >= 0)
        val callVoid = source.indexOf("CallVoidMethod", branchStart)
        val branchEnd = source.indexOf("}", branchStart)
        assertTrue("Expected JNI_ERR return inside !signaturePrefixes branch", source.substring(branchStart, branchEnd).contains("return JNI_ERR;"))
        assertTrue("CallVoidMethod should occur after !signaturePrefixes branch closes", callVoid > branchEnd)
    }

    private fun sourceFile(path: String): String {
        val direct = File(path)
        if (direct.exists()) {
            return direct.readText()
        }
        val fromRepoRoot = File("app", path)
        if (fromRepoRoot.exists()) {
            return fromRepoRoot.readText()
        }
        throw java.io.FileNotFoundException(path)
    }
}
