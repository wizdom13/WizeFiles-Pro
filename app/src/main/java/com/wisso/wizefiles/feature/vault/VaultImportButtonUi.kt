// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.vault

import com.wisso.wizefiles.R

data class VaultImportButtonUiState(
    val enabled: Boolean,
    val textRes: Int
)

object VaultImportButtonUi {
    fun from(isImporting: Boolean): VaultImportButtonUiState =
        if (isImporting) {
            VaultImportButtonUiState(enabled = false, textRes = R.string.vault_importing)
        } else {
            VaultImportButtonUiState(enabled = true, textRes = R.string.vault_import_file)
        }
}
