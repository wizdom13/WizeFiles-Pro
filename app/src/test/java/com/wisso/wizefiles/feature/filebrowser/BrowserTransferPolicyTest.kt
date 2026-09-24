// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import java.nio.file.Paths
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserTransferPolicyTest {
    @Test fun `same provider defaults to move`() {
        val root = Files.createTempDirectory("wize-drag")
        try {
            val source = Files.write(root.resolve("file.txt"), "test".toByteArray())
            val destination = Files.createDirectory(root.resolve("target"))
            val result = BrowserTransferPolicy.validate(listOf(source), destination)
            assertTrue(result.valid)
            assertEquals(BrowserDropAction.MOVE, result.defaultAction)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun `same parent and descendant drops are rejected`() {
        assertFalse(
            BrowserTransferPolicy.validate(
                listOf(Paths.get("/source/file.txt")),
                Paths.get("/source")
            ).valid
        )
        assertFalse(
            BrowserTransferPolicy.validate(
                listOf(Paths.get("/source/folder")),
                Paths.get("/source/folder/child")
            ).valid
        )
    }
}
