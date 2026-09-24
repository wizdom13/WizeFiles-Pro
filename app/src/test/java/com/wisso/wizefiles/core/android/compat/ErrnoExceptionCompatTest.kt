// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.android.compat

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrnoExceptionCompatTest {
    @Test
    fun compatibilityPathDoesNotUseHiddenFieldReflection() {
        val source = projectFile("src/main/java/com/wisso/wizefiles/core/android/compat/ErrnoExceptionCompat.kt")
        assertFalse(source.contains("getDeclaredField"))
        assertFalse(source.contains("isAccessible"))
        assertTrue(source.contains("functionNameFromErrnoMessage(message)"))
    }

    @Test
    fun extractsFunctionNameFromPublicErrnoMessage() {
        assertEquals(
            "open",
            functionNameFromErrnoMessage("open failed: Permission denied")
        )
    }

    @Test
    fun usesSafeFallbackWhenMessageIsUnavailable() {
        assertEquals("syscall", functionNameFromErrnoMessage(null))
        assertEquals("syscall", functionNameFromErrnoMessage(""))
    }

    @Test
    fun usesSafeFallbackWhenPlatformMessageFormatChanges() {
        assertEquals(
            "syscall",
            functionNameFromErrnoMessage("Permission denied")
        )
    }
    private fun projectFile(path: String): String =
        listOf(File(path), File("app/$path")).firstOrNull(File::exists)?.readText()
            ?: error("Missing $path")
}
