// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.vault

object VaultPasswordValidator {
    fun validate(password: String, confirmation: String): String? {
        if (password.isEmpty()) {
            return "empty"
        }
        if (password != confirmation) {
            return "mismatch"
        }
        return null
    }
}
