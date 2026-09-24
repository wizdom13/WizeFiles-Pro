package com.wisso.wizefiles.vault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultOpenSessionChangeDetectorTest {
    @Test
    fun `unchanged size and modified time returns false`() {
        assertFalse(VaultOpenSessionChangeDetector.isChanged(12, 1000, 12, 1000))
    }

    @Test
    fun `changed size returns true`() {
        assertTrue(VaultOpenSessionChangeDetector.isChanged(12, 1000, 13, 1000))
    }

    @Test
    fun `changed modified time returns true`() {
        assertTrue(VaultOpenSessionChangeDetector.isChanged(12, 1000, 12, 2000))
    }
}
