// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.apksigning

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkSigningFoundationSourceContractTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `dependency is pinned licensed and digest checked`() {
        val gradle = source("app/build.gradle")
        val dependencyCheck = source("scripts/verify-apksig-dependency.sh")
        val licenses = source("app/src/main/res/raw/open_libs.html")

        assertTrue("com.github.MuntashirAkon:apksig-android:4.4.0" in gradle)
        assertTrue("948321f77e13368aa0c5f4defe73971578a5f18fdf1b49c11756ef6a15c95586" in dependencyCheck)
        assertTrue("apksig-android:4.4.0" in licenses)
        assertTrue("Apache License" in licenses)
    }

    @Test
    fun `ci exercises r8 fixtures and dependency provenance`() {
        val workflow = source(".github/workflows/android.yml")
        val fixtureCheck = source("scripts/verify-apk-signing-fixtures.sh")

        assertTrue("assembleRelease" in workflow)
        assertTrue("verify-apksig-dependency.sh" in workflow)
        assertTrue("verify-apk-signing-fixtures.sh" in workflow)
        assertTrue("v4-signature-file" in fixtureCheck)
        assertTrue("Verified using v4 scheme" in fixtureCheck)
    }

    @Test
    fun `r8 does not hide the backend behind a broad keep rule`() {
        val rules = source("app/proguard-rules.pro")

        assertFalse("-keep class com.android.apksig.**" in rules)
        assertFalse("-dontwarn com.android.apksig.**" in rules)
    }

    private fun source(path: String): String = File(root, path).readText()
}
