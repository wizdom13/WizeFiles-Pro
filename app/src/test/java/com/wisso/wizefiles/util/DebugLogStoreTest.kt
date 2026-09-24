// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files

class DebugLogStoreTest {
    @Test
    fun `append and export returns accumulated logs`() {
        val directory = Files.createTempDirectory("debug-log-store").toFile()
        val store = DebugLogStore(directory, maxBytes = 1024)

        store.appendLine("first")
        store.appendLine("second")
        val output = ByteArrayOutputStream()
        store.exportTo(output)

        val text = output.toString(Charsets.UTF_8.name())
        assertTrue(text.contains("first"))
        assertTrue(text.contains("second"))
    }

    @Test
    fun `rotation keeps latest file bounded`() {
        val directory = Files.createTempDirectory("debug-log-store").toFile()
        val store = DebugLogStore(directory, maxBytes = 40)

        store.appendLine("012345678901234567890123456789")
        store.appendLine("another line that rotates")

        val all = store.readAll()
        assertTrue(all.contains("012345678901234567890123456789"))
        assertTrue(all.contains("another line that rotates"))
        assertFalse(directory.resolve("app-current.log").length() > 40)
    }
}
