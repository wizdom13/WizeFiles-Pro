// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.vault

import com.wisso.wizefiles.core.app.application
import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.storage.Storage
import com.wisso.wizefiles.storage.VaultStorage
import com.wisso.wizefiles.util.AppLog
import com.wisso.wizefiles.util.valueCompat

internal fun repairVaultStorages(
    existingStorages: List<Storage>,
    vaultMetadatas: List<VaultMetadata>
): List<Storage> {
    if (vaultMetadatas.isEmpty()) {
        return existingStorages
    }
    val vaultMetadatasById = vaultMetadatas.associateBy { it.vaultId }
    val repairedStorages = existingStorages.map { storage ->
        if (storage is VaultStorage) {
            val metadata = vaultMetadatasById[storage.vaultId]
            if (metadata != null && storage.vaultName != metadata.name) {
                storage.copy(vaultName = metadata.name)
            } else {
                storage
            }
        } else {
            storage
        }
    }.toMutableList()
    val existingVaultIds = repairedStorages.filterIsInstance<VaultStorage>().map { it.vaultId }.toSet()
    val missingVaultStorages = vaultMetadatas
        .filterNot { it.vaultId in existingVaultIds }
        .sortedBy { it.createdAt }
        .map { metadata ->
            VaultStorage(
                vaultId = metadata.vaultId,
                vaultName = metadata.name,
                isVisible = true
            )
        }
    if (missingVaultStorages.isEmpty()) {
        return repairedStorages
    }
    repairedStorages += missingVaultStorages
    return repairedStorages
}

internal fun repairVaultStoragesFromRepository() {
    val currentStorages = Settings.STORAGES.valueCompat
    val repairedStorages = repairVaultStorages(
        existingStorages = currentStorages,
        vaultMetadatas = VaultManager(application).listVaults()
    )
    if (repairedStorages == currentStorages) {
        return
    }
    val recoveredVaultCount = repairedStorages.filterIsInstance<VaultStorage>().size -
        currentStorages.filterIsInstance<VaultStorage>().size
    AppLog.w(
        "VaultStorageRepair",
        "Repairing vault navigation entries. recoveredVaultCount=$recoveredVaultCount"
    )
    Settings.STORAGES.putValue(repairedStorages)
}
