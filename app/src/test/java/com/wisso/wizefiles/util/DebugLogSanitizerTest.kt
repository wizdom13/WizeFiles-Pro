package com.wisso.wizefiles.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugLogSanitizerTest {
    @Test
    fun `sanitize redacts credential fields and authorization headers`() {
        val input = "password=abc token:xyz authorization=Bearer qwerty cookie=session123"

        val sanitized = DebugLogSanitizer.sanitize(input)

        assertFalse(sanitized.contains("abc"))
        assertFalse(sanitized.contains("xyz"))
        assertFalse(sanitized.contains("qwerty"))
        assertFalse(sanitized.contains("session123"))
        assertTrue(sanitized.contains("password=<redacted>"))
        assertTrue(sanitized.contains("token=<redacted>"))
    }

    @Test
    fun `sanitize redacts url secrets and credentials in uri`() {
        val input = "https://user:pass@example.com/api?access_token=mytoken&name=ok"

        val sanitized = DebugLogSanitizer.sanitize(input)

        assertFalse(sanitized.contains("user"))
        assertFalse(sanitized.contains("pass"))
        assertFalse(sanitized.contains("mytoken"))
        assertTrue(sanitized.contains("https://<redacted>@example.com"))
        assertTrue(sanitized.contains("access_token=<redacted>"))
        assertTrue(sanitized.contains("name=ok"))
    }

    @Test
    fun `sanitize throwable redacts nested cause chain in stack trace`() {
        val nested = IllegalStateException(
            "authorization: Bearer nestedtoken cookie=session123 https://user:pass@example.com?token=abc123"
        )
        val top = RuntimeException("password=secretTop", nested)

        val sanitizedThrowable = AppLog.sanitizeThrowableForTest(top)
        val writer = java.io.StringWriter()
        sanitizedThrowable.printStackTrace(java.io.PrintWriter(writer))
        val stackTrace = writer.toString()

        assertFalse(stackTrace.contains("secretTop"))
        assertFalse(stackTrace.contains("nestedtoken"))
        assertFalse(stackTrace.contains("session123"))
        assertFalse(stackTrace.contains("user:pass"))
        assertFalse(stackTrace.contains("abc123"))
        assertTrue(stackTrace.contains("password=<redacted>"))
        assertTrue(stackTrace.contains("authorization=<redacted>"))
        assertTrue(stackTrace.contains("cookie=<redacted>"))
        assertTrue(stackTrace.contains("https://<redacted>@example.com?token=<redacted>"))
    }


    @Test
    fun `sanitize throwable redacts suppressed exceptions in stack trace`() {
        val suppressed = IllegalArgumentException(
            "authorization: Bearer suppressedToken cookie=sessionSuppressed https://supuser:suppass@example.com?token=supabc"
        )
        val top = RuntimeException("password=mainSecret")
        top.addSuppressed(suppressed)

        val sanitizedThrowable = AppLog.sanitizeThrowableForTest(top)
        val writer = java.io.StringWriter()
        sanitizedThrowable.printStackTrace(java.io.PrintWriter(writer))
        val stackTrace = writer.toString()

        assertFalse(stackTrace.contains("mainSecret"))
        assertFalse(stackTrace.contains("suppressedToken"))
        assertFalse(stackTrace.contains("sessionSuppressed"))
        assertFalse(stackTrace.contains("supuser:suppass"))
        assertFalse(stackTrace.contains("supabc"))
        assertTrue(stackTrace.contains("password=<redacted>"))
        assertTrue(stackTrace.contains("authorization=<redacted>"))
        assertTrue(stackTrace.contains("cookie=<redacted>"))
        assertTrue(stackTrace.contains("https://<redacted>@example.com?token=<redacted>"))
        assertTrue(sanitizedThrowable.suppressed.isNotEmpty())
        assertNotSame(suppressed, sanitizedThrowable.suppressed.first())
    }

    @Test
    fun `sanitize throwable retains original exception types`() {
        val nested = java.nio.file.NoSuchFileException("/Track No08.mp3")
        val top = IllegalStateException("metadata failed", nested)

        val sanitizedThrowable = AppLog.sanitizeThrowableForTest(top)
        val writer = java.io.StringWriter()
        sanitizedThrowable.printStackTrace(java.io.PrintWriter(writer))
        val stackTrace = writer.toString()

        assertTrue(stackTrace.contains("java.lang.IllegalStateException"))
        assertTrue(stackTrace.contains("java.nio.file.NoSuchFileException"))
    }
}
