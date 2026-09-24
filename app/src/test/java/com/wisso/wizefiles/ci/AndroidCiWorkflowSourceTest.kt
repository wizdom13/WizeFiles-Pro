// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.ci

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidCiWorkflowSourceTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `pull requests use the wrapper for clean JVM and release verification`() {
        val workflow = File(root, ".github/workflows/android.yml").readText()
        val cleanBuild = File(root, "scripts/verify-clean-build.sh").readText()

        assertTrue("- name: Verify clean debug build and JVM tests" in workflow)
        assertTrue("WIZEFILES_CLEAN_GRADLE_CACHE: '1'" in workflow)
        assertTrue("run: scripts/verify-clean-build.sh" in workflow)
        assertTrue("./gradlew --no-daemon clean" in cleanBuild)
        // This source-level policy is appropriate because the invariant is the checked-in CI
        // command itself: every pure JVM module and both complete app test partitions must run.
        assertTrue(":core-files-api:test" in cleanBuild)
        assertTrue(":feature-browser-domain:test" in cleanBuild)
        assertTrue(":feature-transfer-domain:test" in cleanBuild)
        assertTrue(":feature-vault-domain:test" in cleanBuild)
        assertTrue(":performance-baselines:test" in cleanBuild)
        assertTrue(":app:assembleDebug" in cleanBuild)
        assertTrue(":app:testPureDebugUnitTest" in cleanBuild)
        assertTrue(":app:testRobolectricDebugUnitTest" in cleanBuild)
        assertTrue("- name: Build minified release and run release lint" in workflow)
        assertTrue("run: ./gradlew assembleRelease lintVitalRelease --warning-mode all" in workflow)
        assertFalse("if: github.event_name != 'pull_request'" in workflow)
        assertFalse(":app:compileBetaKotlin" in workflow)
        assertFalse("assembleDebug assembleRelease" in workflow)
    }

    @Test
    fun `dependency audit requires authenticated NVD access`() {
        val workflow = File(root, ".github/workflows/android.yml").readText()

        assertTrue("NVD_API_KEY: \${{ secrets.NVD_API_KEY }}" in workflow)
        assertTrue("- name: Check NVD API key availability" in workflow)
        assertTrue("NVD_API_KEY repository secret is required on non-PR runs" in workflow)
        assertTrue("nvd.apiKey = System.getenv('NVD_API_KEY')" in workflow)
        assertTrue("--init-script \"\$RUNNER_TEMP/dependency-check.init.gradle\"" in workflow)
    }

    @Test
    fun `dependency audit targets shipped runtime and documents suppressions`() {
        val appBuild = File(root, "app/build.gradle").readText()
        val suppressions = File(root, "config/dependency-check-suppressions.xml")

        assertTrue(
            "scanConfigurations = ['releaseRuntimeClasspath']" in appBuild
        )
        assertTrue("config/dependency-check-suppressions.xml" in appBuild)
        assertTrue("implementation 'org.apache.commons:commons-lang3:3.20.0'" in appBuild)
        assertTrue(suppressions.isFile)
        assertTrue("<suppressions" in suppressions.readText())
    }

    @Test
    fun `JitPack is scoped to GitHub hosted artifacts`() {
        val rootBuild = File(root, "build.gradle").readText()

        assertTrue("repositories.whenObjectAdded" in rootBuild)
        assertTrue("repository.url.host == 'jitpack.io'" in rootBuild)
        assertTrue("includeGroupByRegex 'com\\\\.github\\\\..*'" in rootBuild)
    }
}
