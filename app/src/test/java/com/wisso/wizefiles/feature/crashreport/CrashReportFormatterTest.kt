package com.wisso.wizefiles.feature.crashreport

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportFormatterTest {
    private val metadata = CrashReportMetadata(
        appVersion = "1.2.3",
        versionCode = 123,
        buildType = "debug",
        timestamp = "2026-08-14T10:00:00Z",
        androidVersion = "16",
        apiLevel = 36,
        manufacturer = "Example",
        model = "Device",
        processName = "com.wisso.wizefiles",
        collectorThread = "main"
    )

    @Test
    fun `formats minimum report without optional user data`() {
        val report = CrashReportFormatter.format(metadata, "Example stack")

        assertTrue("App: 1.2.3 (123)" in report)
        assertTrue("Android: 16 (API 36)" in report)
        assertTrue("Example stack" in report)
        assertFalse("Recent diagnostic log" in report)
    }

    @Test
    fun `adds comment and log only when supplied`() {
        val report = CrashReportFormatter.appendUserDetails(
            baseReport = CrashReportFormatter.format(metadata, "Example stack"),
            comment = "Opened a folder",
            diagnosticLog = "INFO safe line"
        )

        assertTrue("User comment" in report)
        assertTrue("Opened a folder" in report)
        assertTrue("user opted in" in report)
        assertTrue("INFO safe line" in report)
    }
}
