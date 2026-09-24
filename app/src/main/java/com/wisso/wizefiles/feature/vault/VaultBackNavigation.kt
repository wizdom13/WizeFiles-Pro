// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.vault

internal enum class VaultBackAction {
    NAVIGATE_TO_PARENT,
    SHOW_LEAVE_DIALOG
}

internal fun resolveVaultBackAction(hasParentInStack: Boolean): VaultBackAction =
    if (hasParentInStack) VaultBackAction.NAVIGATE_TO_PARENT else VaultBackAction.SHOW_LEAVE_DIALOG

