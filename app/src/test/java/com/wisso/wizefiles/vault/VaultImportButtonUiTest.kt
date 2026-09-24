package com.wisso.wizefiles.vault

import com.wisso.wizefiles.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultImportButtonUiTest {
    @Test
    fun `from importing returns disabled importing state`() {
        val state = VaultImportButtonUi.from(isImporting = true)

        assertFalse(state.enabled)
        assertEquals(R.string.vault_importing, state.textRes)
    }

    @Test
    fun `from idle returns enabled import state`() {
        val state = VaultImportButtonUi.from(isImporting = false)

        assertTrue(state.enabled)
        assertEquals(R.string.vault_import_file, state.textRes)
    }
}
