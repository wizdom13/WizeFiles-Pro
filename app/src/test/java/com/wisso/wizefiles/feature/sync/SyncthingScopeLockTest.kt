// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import org.junit.Assert.*
import org.junit.Test

class SyncthingScopeLockTest {
    @Test fun `engine and filesystem URI spellings share the same scope lock`() {
        val locks = SyncScopeLockRegistry()
        assertTrue(locks.tryAcquire("filesystem", listOf("file:///storage/emulated/0/Documents")))
        assertFalse(locks.tryAcquire("syncthing", listOf("file:/storage/emulated/0/Documents/nested")))
        assertFalse(locks.tryAcquire("alias", listOf("file:///storage/emulated/0/Music/../Documents")))
        locks.release("filesystem")
        assertTrue(locks.tryAcquire("syncthing", listOf("file:/storage/emulated/0/Documents")))
    }

    @Test fun `profiles cannot silently opt into deletions or an unsupported conflict policy`() {
        val profile = SyncProfile(name = "test", sourceUri = "file:///documents",
            destinationUri = SyncthingEndpointCodec.encode(SyncthingEndpoint("PEER", "folder")),
            mode = SyncMode.TWO_WAY)
        assertThrows(IllegalArgumentException::class.java) { SyncthingProfilePolicy.validate(profile) }
        assertThrows(IllegalArgumentException::class.java) {
            SyncthingProfilePolicy.validate(profile.copy(propagateDeletions = true, conflictPolicy = SyncConflictPolicy.ASK))
        }
        assertEquals(SyncthingFolderMode.SEND_RECEIVE,
            SyncthingProfilePolicy.folderMode(profile.copy(propagateDeletions = true)))
    }
}
