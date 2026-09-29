// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SyncthingVersionPolicyTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun nativeArchiveNamesMustNotCollideWithinTheSameSecond() {
        val timestamp = "2026-09-29T11:54:52Z"
        val now = java.time.Instant.parse(timestamp).toEpochMilli() + 250
        assertEquals(850L, SyncthingVersionPolicy.restoreDelayMillis(listOf(timestamp), now))
        assertEquals(0L, SyncthingVersionPolicy.restoreDelayMillis(listOf(timestamp), now + 1000))
    }
    @Test fun restoreRejectsTraversalAndSymbolicLinks() {
        val root = temporary.newFolder("folder")
        val outside = temporary.newFolder("outside")
        Files.createSymbolicLink(File(root, "link").toPath(), outside.toPath())
        listOf("../secret", "/tmp/secret", "link/secret", "a/../../secret", "a\\..\\secret", "").forEach {
            assertThrows(IllegalArgumentException::class.java) { SyncthingVersionPolicy.safeFile(root, it) }
        }
        assertEquals(File(root, "notes/file.txt"), SyncthingVersionPolicy.safeFile(root, "notes/file.txt"))
    }
    @Test fun conflictListIgnoresVersionsAndDoesNotFollowLinks() {
        val root = temporary.newFolder("folder")
        File(root, "notes.sync-conflict-20260929.txt").writeText("conflict")
        File(root, ".stversions").mkdir()
        File(root, ".stversions/old.sync-conflict-20250101.txt").writeText("old")
        val outside = temporary.newFolder("outside")
        File(outside, "outside.sync-conflict-20260929.txt").writeText("outside")
        Files.createSymbolicLink(File(root, "link").toPath(), outside.toPath())
        val result = SyncthingVersionPolicy.conflicts(root)
        assertEquals(listOf("notes.sync-conflict-20260929.txt"), result.names)
        assertFalse(result.truncated)
    }
    @Test fun retentionCanBeDisabledAndPreservesOtherProtectionSettings() {
        val saved = SyncthingVersionPolicy.encode("{\"custom\":true}", 17)
        assertEquals(17, SyncthingVersionPolicy.keep(saved))
        assertTrue(org.json.JSONObject(saved).getBoolean("custom"))
        assertEquals(0, SyncthingVersionPolicy.keep(SyncthingVersionPolicy.encode(saved, 0)))
        assertThrows(IllegalArgumentException::class.java) { SyncthingVersionPolicy.encode(saved, 101) }
    }
}
