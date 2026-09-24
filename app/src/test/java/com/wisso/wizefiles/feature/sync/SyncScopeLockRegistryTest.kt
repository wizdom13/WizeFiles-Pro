package com.wisso.wizefiles.feature.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncScopeLockRegistryTest {
    @Test
    fun locksBothRootsForTwoWayRun() {
        val registry = SyncScopeLockRegistry()
        assertTrue(registry.tryAcquire("two-way", listOf("file:///a", "rclone://drive/b")))
        assertFalse(registry.tryAcquire("copy", listOf("rclone://drive/b/subfolder")))
        registry.release("two-way")
        assertTrue(registry.tryAcquire("copy", listOf("rclone://drive/b/subfolder")))
    }
}
