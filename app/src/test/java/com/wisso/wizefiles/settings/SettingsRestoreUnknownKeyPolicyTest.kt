package com.wisso.wizefiles.settings

import com.wisso.wizefiles.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRestoreUnknownKeyPolicyTest {
    @Test
    fun `unknown settings key is skipped while known settings remain restorable`() {
        val validKey = SettingsRestoreTestKeys.key(R.string.pref_key_recycle_bin)
        val preview = SettingsRestoreValidator(
            context = SettingsRestoreTestKeys.mockContext,
            keySet = SettingsRestoreTestKeys.allKeys,
            hasSecurityPassword = { true },
            hasRootCapability = { true },
            keyResolver = { id -> SettingsRestoreTestKeys.key(id) }
        ).buildPreview(
            SettingsBackupData(
                2,
                mapOf(
                    "key_hidden_api_bypass" to BackupValue("boolean", true),
                    validKey to BackupValue("boolean", false)
                ),
                emptyMap()
            )
        )

        assertEquals(setOf(validKey), preview.settingsToApply.keys)
        assertEquals("Setting is no longer supported", preview.skippedSettings["key_hidden_api_bypass"])
        assertTrue(preview.warnings.any { it.contains("obsolete or incompatible") })
    }
}
