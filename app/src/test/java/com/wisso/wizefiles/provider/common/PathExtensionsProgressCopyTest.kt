// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PathExtensionsProgressCopyTest {

    @Test
    fun progressOption_onNonLinuxPath_usesForeignCopyMoveFallback() {
        val source = Files.createTempFile("path-copy-source", ".txt")
        val target = Files.createTempFile("path-copy-target", ".txt")
        val options = arrayOf<CopyOption>(ProgressCopyOption(1L) { })

        assertTrue(shouldUseForeignCopyMoveForProgress(source, target, options))
    }

    @Test
    fun copyTo_onNonLinuxPath_withProgressOption_copiesSuccessfully() {
        val source = Files.createTempFile("path-copy-source", ".txt")
        val target = Files.createTempFile("path-copy-target", ".txt")
        Files.write(source, "source".toByteArray())
        Files.write(target, "target".toByteArray())
        var progress = 0L

        source.copyTo(
            target,
            StandardCopyOption.REPLACE_EXISTING,
            ProgressCopyOption(1L) { progress += it }
        )

        assertEquals("source", String(Files.readAllBytes(target)))
        assertTrue(progress > 0L)
    }

    @Test
    fun noProgressOption_keepsSameProviderCopyPath() {
        val source = Files.createTempFile("path-copy-source", ".txt")
        val target = Files.createTempFile("path-copy-target", ".txt")

        assertFalse(shouldUseForeignCopyMoveForProgress(source, target, emptyArray()))
    }
}
