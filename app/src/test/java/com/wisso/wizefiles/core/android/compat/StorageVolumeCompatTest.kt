// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.android.compat

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StorageVolumeCompatTest {

    @Test
    fun `missing directory accessor does not throw and falls back to primary directory`() {
        val fallback = File("/storage/emulated/0")

        val resolved = resolveStorageVolumePathFileCompat(
            directoryProvider = { throw NoSuchMethodException("StorageVolume.getPath") },
            primaryDirectoryProvider = { fallback }
        )

        assertEquals(fallback, resolved)
    }

    @Test
    fun `missing directory accessor returns null when no fallback exists`() {
        val resolved = resolveStorageVolumePathFileCompat(
            directoryProvider = { throw NoSuchMethodException("StorageVolume.getPath") },
            primaryDirectoryProvider = { null }
        )

        assertNull(resolved)
    }
}
