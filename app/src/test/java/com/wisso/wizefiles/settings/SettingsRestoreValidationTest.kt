// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.settings

import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRestoreValidationTest {

    private val serializer = SettingsBackupSerializer()

    @Test(expected = IllegalArgumentException::class)
    fun `rejects missing schema version`() {
        serializer.deserialize("""{"encrypted":false,"settings":{}}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects malformed schema version type`() {
        serializer.deserialize("""{"formatVersion":"x","encrypted":false,"settings":{}}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects unknown root fields in v2 schema`() {
        serializer.deserialize(
            """{"formatVersion":2,"encrypted":false,"settings":{},"secretSettings":{},"unexpected":1}"""
        )
    }

    @Test
    fun `restore manager defines json size cap constant`() {
        assertTrue(SettingsBackupRestoreManager.MAX_SETTINGS_BACKUP_JSON_BYTES <= 262_144)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `double encoded path payload is rejected for serialized path key`() {
        SettingsRestoreValidator(
            context = SettingsRestoreTestKeys.mockContext,
            keySet = SettingsRestoreTestKeys.allKeys,
            hasSecurityPassword = { true },
            hasRootCapability = { true },
            keyResolver = { id -> SettingsRestoreTestKeys.key(id) }
        ).buildPreview(
            SettingsBackupData(
                2,
                mapOf(
                    SettingsRestoreTestKeys.key(com.wisso.wizefiles.R.string.pref_key_file_list_default_directory) to
                        BackupValue("string", "file%253A%252F%252F%252Fstorage%252Femulated%252F0")
                ),
                emptyMap()
            )
        )
    }
}
