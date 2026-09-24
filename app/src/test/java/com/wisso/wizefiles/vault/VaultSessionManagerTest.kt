// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.vault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultSessionManagerTest {
    @Test
    fun putAndLockUpdatesSessionState() {
        val vaultId = "vault-session-test"
        VaultSessionManager.lock(vaultId)

        VaultSessionManager.putUnlockedKey(vaultId, byteArrayOf(1, 2, 3))
        assertTrue(VaultSessionManager.isUnlocked(vaultId))

        VaultSessionManager.lock(vaultId)
        assertFalse(VaultSessionManager.isUnlocked(vaultId))
    }

    @Test
    fun clearAllRemovesAllSessions() {
        VaultSessionManager.putUnlockedKey("vault-a", byteArrayOf(1))
        VaultSessionManager.putUnlockedKey("vault-b", byteArrayOf(2))

        VaultSessionManager.clearAll()

        assertFalse(VaultSessionManager.isUnlocked("vault-a"))
        assertFalse(VaultSessionManager.isUnlocked("vault-b"))
    }

    @Test
    fun backgroundGraceEndsByZeroingAndRemovingTheKey() {
        val vaultId = "vault-background-test"
        VaultSessionManager.putUnlockedKey(vaultId, byteArrayOf(9, 8, 7))
        VaultSessionManager.markBackgrounded(vaultId)
        assertTrue(VaultSessionManager.isUnlocked(vaultId))

        VaultSessionManager.enforceRelockDeadline(vaultId)

        assertFalse(VaultSessionManager.isUnlocked(vaultId))
        assertTrue(VaultSessionManager.getUnlockedKey(vaultId) == null)
    }
}
