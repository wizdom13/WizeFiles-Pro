// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultPolicyTest {
    @Test
    fun `entry name conflicts are scoped to parent and permit the renamed entry`() {
        val entry = TestVaultEntryIdentity("entry", "parent", "report")
        assertTrue(VaultEntryNamePolicy.hasConflict(listOf(entry), "parent", "report"))
        assertFalse(VaultEntryNamePolicy.hasConflict(listOf(entry), "other", "report"))
        assertFalse(VaultEntryNamePolicy.hasConflict(listOf(entry), "parent", "report", "entry"))
        assertThrows(VaultEntryNameConflictException::class.java) {
            VaultEntryNamePolicy.requireAvailable(listOf(entry), "parent", "report")
        }
    }

    @Test fun `locked vault cannot mutate`() {
        assertEquals(
            VaultMutationDecision.RequiresUnlock,
            VaultMutationPolicy.decide(VaultMutationKind.DELETE, VaultMutationContext(unlocked = false))
        )
    }

    @Test fun `failed commit requires rollback before clean state`() {
        var state = VaultRecoveryReducer.reduce(VaultRecoveryState.CLEAN, VaultRecoveryEvent.BeginWrite)
        state = VaultRecoveryReducer.reduce(state, VaultRecoveryEvent.CommitFailed)
        assertEquals(VaultRecoveryState.ROLLBACK_REQUIRED, state)
        assertThrows(IllegalArgumentException::class.java) {
            VaultRecoveryReducer.reduce(state, VaultRecoveryEvent.BeginWrite)
        }
        assertEquals(VaultRecoveryState.CLEAN, VaultRecoveryReducer.reduce(state, VaultRecoveryEvent.RollbackSucceeded))
    }

    private data class TestVaultEntryIdentity(
        override val id: String,
        override val parentId: String?,
        override val name: String
    ) : VaultEntryIdentity
}
