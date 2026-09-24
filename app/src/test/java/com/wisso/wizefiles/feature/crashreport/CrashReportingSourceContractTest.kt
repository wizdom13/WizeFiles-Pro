// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.crashreport

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportingSourceContractTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `crash capture stays local and user controlled`() {
        val build = source("app/build.gradle")
        val manifest = source("app/src/main/AndroidManifest.xml")
        val sender = source(
            "app/src/main/java/com/wisso/wizefiles/feature/crashreport/AcraReportSender.java"
        )
        val activity = source(
            "app/src/main/java/com/wisso/wizefiles/feature/crashreport/CrashReportActivity.kt"
        )
        val privacy = source("web/privacy.html")

        assertTrue("implementation 'ch.acra:acra-core:5.13.1'" in build)
        assertFalse("acra-http" in build)
        assertTrue("merges += ['META-INF/services/**']" in build)
        assertTrue("android:name=\".WizeFilesApplication\"" in manifest)
        assertTrue("CrashReportActivity" in manifest)
        assertTrue("ReportField.STACK_TRACE" in sender)
        assertTrue("CrashReportStore.INSTANCE.save" in sender)
        assertFalse("HttpSender" in sender)
        assertTrue("binding.includeDiagnosticLog.isChecked" in activity)
        assertTrue("private fun markReportHandled()" in activity)
        assertTrue("CrashReportStore.delete(this)" in activity)
        assertTrue(activity.split("markReportHandled()").size - 1 >= 4)
        assertTrue("Nothing is uploaded automatically" in source("app/src/main/res/values/strings.xml"))
        assertTrue("It is not uploaded automatically." in privacy)
    }

    private fun source(path: String): String = File(root, path).readText()
}
