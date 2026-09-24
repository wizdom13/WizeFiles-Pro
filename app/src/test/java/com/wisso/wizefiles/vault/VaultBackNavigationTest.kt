package com.wisso.wizefiles.vault

import org.junit.Assert.assertEquals
import org.junit.Test

class VaultBackNavigationTest {

    @Test
    fun resolveVaultBackAction_navigatesToParent_whenParentExists() {
        assertEquals(
            VaultBackAction.NAVIGATE_TO_PARENT,
            resolveVaultBackAction(hasParentInStack = true)
        )
    }

    @Test
    fun resolveVaultBackAction_showsLeaveDialog_whenAtRoot() {
        assertEquals(
            VaultBackAction.SHOW_LEAVE_DIALOG,
            resolveVaultBackAction(hasParentInStack = false)
        )
    }
}
