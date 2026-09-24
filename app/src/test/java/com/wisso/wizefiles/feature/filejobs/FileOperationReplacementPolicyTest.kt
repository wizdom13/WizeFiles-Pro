// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filejobs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import com.wisso.wizefiles.provider.ftp.FtpFileSystemProvider

class FileOperationReplacementPolicyTest {
    @Test
    fun `replacement only permits matching file types`() {
        assertTrue(canReplaceTarget(sourceIsDirectory = false, targetIsDirectory = false))
        assertTrue(canReplaceTarget(sourceIsDirectory = true, targetIsDirectory = true))
        assertFalse(canReplaceTarget(sourceIsDirectory = false, targetIsDirectory = true))
        assertFalse(canReplaceTarget(sourceIsDirectory = true, targetIsDirectory = false))
    }

    @Test(expected = FileAlreadyExistsException::class)
    fun `local provider rejects file replacing directory before mutation`() {
        val root = Files.createTempDirectory("replacement-policy")
        try {
            val source = Files.createTempFile(root, "source", ".txt")
            val target = Files.createDirectory(root.resolve("target"))
            requireCompatibleReplacement(
                source,
                target,
                FileAlreadyExistsException(target.toString()),
                { Files.isDirectory(it) }
            )
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `remote provider rejects directory replacing file without provider mutation`() {
        val source = FtpFileSystemProvider.getPath(URI("ftp://user@example.com/source"))
        val target = FtpFileSystemProvider.getPath(URI("ftp://user@example.com/target"))
        try {
            requireCompatibleReplacement(
                source,
                target,
                FileAlreadyExistsException(target.toString())
            ) { path -> path == source }
            throw AssertionError("Expected incompatible replacement to be rejected")
        } catch (_: FileAlreadyExistsException) {
            // The provider mutation callback is intentionally downstream of validation.
        }
    }
}
