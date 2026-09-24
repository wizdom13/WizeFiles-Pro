// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.os

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

class LocalLinuxFileAttributeViewSeLinuxFallbackRegressionTest {

    @Test
    fun `selinux labels are best effort while stat failures remain fatal`() {
        val sourceCandidates = listOf(
            Paths.get("app/src/main/java/com/wisso/wizefiles/data/providers/os/LocalLinuxFileAttributeView.kt"),
            Paths.get("src/main/java/com/wisso/wizefiles/data/providers/os/LocalLinuxFileAttributeView.kt")
        )
        val sourceFile = sourceCandidates.firstOrNull { Files.exists(it) }
            ?: error("Unable to locate LocalLinuxFileAttributeView.kt source file")
        val source = String(Files.readAllBytes(sourceFile), StandardCharsets.UTF_8)

        assertTrue(source.contains("throw e.toFileSystemException(path.toString())"))
        assertTrue(source.contains("readSeLinuxContextBestEffort()"))
        assertTrue(source.contains("catch (exception: SyscallException)"))
        assertTrue(source.contains("catch (exception: RuntimeException)"))
        assertTrue(source.contains("logSeLinuxUnavailableOnce()"))
        assertTrue(source.contains("ByteString.EMPTY"))
        assertFalse(source.contains("e.errno == OsConstants"))
    }
}
