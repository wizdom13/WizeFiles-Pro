package com.wisso.wizefiles.feature.crashreport

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportSanitizerTest {
    @Test
    fun `redacts secrets paths uris and email addresses`() {
        val sanitized = CrashReportSanitizer.sanitize(
            "password=hunter2 Authorization: Bearer abc " +
                "content://provider/private/file.txt /storage/emulated/0/Secret/file.txt " +
                "person@example.com"
        )

        assertFalse("hunter2" in sanitized)
        assertFalse("abc" in sanitized)
        assertFalse("private/file.txt" in sanitized)
        assertFalse("Secret/file.txt" in sanitized)
        assertFalse("person@example.com" in sanitized)
        assertTrue("<redacted-uri>" in sanitized)
        assertTrue("<redacted-path>" in sanitized)
        assertTrue("<redacted-email>" in sanitized)
    }

    @Test
    fun `keeps diagnostic stack structure`() {
        val sanitized = CrashReportSanitizer.sanitize(
            "java.lang.IllegalStateException: failed\n\tat com.wisso.Feature.open(Feature.kt:42)"
        )

        assertTrue("IllegalStateException" in sanitized)
        assertTrue("Feature.kt:42" in sanitized)
    }
}
