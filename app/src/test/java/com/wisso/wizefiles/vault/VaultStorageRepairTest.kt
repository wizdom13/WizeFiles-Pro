// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.vault

import com.wisso.wizefiles.storage.FileSystemRoot
import com.wisso.wizefiles.storage.VaultStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultStorageRepairTest {
    @Test
    fun repairVaultStorages_addsMissingVaultsWithoutDroppingExistingStorages() {
        val existingStorages = listOf(
            FileSystemRoot(customName = null, isVisible = true),
            VaultStorage(vaultId = "vault-1", vaultName = "Personal")
        )

        val repaired = repairVaultStorages(
            existingStorages = existingStorages,
            vaultMetadatas = listOf(
                vaultMetadata(vaultId = "vault-1", name = "Personal", createdAt = 1L),
                vaultMetadata(vaultId = "vault-2", name = "Work", createdAt = 2L)
            )
        )

        assertEquals(3, repaired.size)
        assertTrue(repaired.first() is FileSystemRoot)
        assertEquals(
            listOf("vault-1", "vault-2"),
            repaired.filterIsInstance<VaultStorage>().map { it.vaultId }
        )
    }

    @Test
    fun repairVaultStorages_updatesExistingVaultNamesWithoutDuplicatingEntries() {
        val repaired = repairVaultStorages(
            existingStorages = listOf(VaultStorage(vaultId = "vault-1", vaultName = "Old Name")),
            vaultMetadatas = listOf(vaultMetadata(vaultId = "vault-1", name = "New Name", createdAt = 1L))
        )

        val vaultStorages = repaired.filterIsInstance<VaultStorage>()
        assertEquals(1, vaultStorages.size)
        assertEquals("New Name", vaultStorages.single().vaultName)
    }

    private fun vaultMetadata(vaultId: String, name: String, createdAt: Long): VaultMetadata = VaultMetadata(
        version = 1,
        vaultId = vaultId,
        name = name,
        createdAt = createdAt,
        saltBase64 = "salt",
        argon2Params = Argon2Params(memoryKiB = 1, iterations = 1, parallelism = 1),
        wrappedVmkBase64 = "wrapped",
        vmkWrapIvBase64 = "iv",
        biometricEnabled = false,
        biometricWrappedVmkBase64 = null,
        biometricIvBase64 = null,
        biometricKeyAlias = "alias"
    )
}
