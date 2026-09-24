// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.settings

import com.wisso.wizefiles.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRestoreDangerousSettingGateTest {
    @Test
    fun `app lock and biometric settings are skipped when no security password exists`() {
        val validator = SettingsRestoreValidator(
            context = SettingsRestoreTestKeys.mockContext,
            keySet = SettingsRestoreTestKeys.allKeys,
            hasSecurityPassword = { false },
            hasRootCapability = { true },
            keyResolver = { id -> SettingsRestoreTestKeys.key(id) }
        )
        val preview = validator.buildPreview(
            SettingsBackupData(
                2,
                mapOf(
                    SettingsRestoreTestKeys.key(R.string.pref_key_protect_browser) to BackupValue("boolean", true),
                    SettingsRestoreTestKeys.key(R.string.pref_key_enable_biometric) to BackupValue("boolean", true)
                ),
                emptyMap()
            )
        )

        assertTrue(preview.settingsToApply.isEmpty())
        assertTrue(preview.skippedSettings.containsKey(SettingsRestoreTestKeys.key(R.string.pref_key_protect_browser)))
        assertTrue(preview.warnings.isNotEmpty())
    }

    @Test
    fun `root strategy always is skipped when root capability missing`() {
        val validator = SettingsRestoreValidator(
            context = SettingsRestoreTestKeys.mockContext,
            keySet = SettingsRestoreTestKeys.allKeys,
            hasSecurityPassword = { true },
            hasRootCapability = { false },
            keyResolver = { id -> SettingsRestoreTestKeys.key(id) }
        )
        val preview = validator.buildPreview(
            SettingsBackupData(
                2,
                mapOf(SettingsRestoreTestKeys.key(R.string.pref_key_root_strategy) to BackupValue("string", "2")),
                emptyMap()
            )
        )

        assertFalse(preview.settingsToApply.containsKey(SettingsRestoreTestKeys.key(R.string.pref_key_root_strategy)))
        assertTrue(preview.skippedSettings.containsKey(SettingsRestoreTestKeys.key(R.string.pref_key_root_strategy)))
    }
}
