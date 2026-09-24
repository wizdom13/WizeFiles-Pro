package com.wisso.wizefiles.vault

internal enum class VaultBackAction {
    NAVIGATE_TO_PARENT,
    SHOW_LEAVE_DIALOG
}

internal fun resolveVaultBackAction(hasParentInStack: Boolean): VaultBackAction =
    if (hasParentInStack) VaultBackAction.NAVIGATE_TO_PARENT else VaultBackAction.SHOW_LEAVE_DIALOG

