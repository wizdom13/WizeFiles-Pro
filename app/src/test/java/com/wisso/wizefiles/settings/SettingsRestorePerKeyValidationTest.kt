package com.wisso.wizefiles.settings

import com.wisso.wizefiles.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Test

class SettingsRestorePerKeyValidationTest {
    private fun validator(
        hasSecurityPassword: Boolean = true,
        hasRootCapability: Boolean = true
    ): SettingsRestoreValidator = SettingsRestoreValidator(
        context = SettingsRestoreTestKeys.mockContext,
        keySet = SettingsRestoreTestKeys.allKeys,
        hasSecurityPassword = { hasSecurityPassword },
        hasRootCapability = { hasRootCapability },
        keyResolver = { id -> SettingsRestoreTestKeys.key(id) }
    )

    @Test
    fun `every restorable key has explicit policy`() {
        val preview = validator().buildPreview(SettingsBackupData(2, emptyMap(), emptyMap()))
        assertTrue(preview.settingsToApply.isEmpty())
    }

    @Test
    fun `invalid ordinary setting is skipped while valid settings remain applicable`() {
        val invalidKey = SettingsRestoreTestKeys.key(R.string.pref_key_night_mode)
        val invalidIconKey = SettingsRestoreTestKeys.key(R.string.pref_key_file_icon_shape)
        val validKey = SettingsRestoreTestKeys.key(R.string.pref_key_recycle_bin)
        val preview = validator().buildPreview(
            SettingsBackupData(
                2,
                mapOf(
                    invalidKey to BackupValue("string", "99"),
                    invalidIconKey to BackupValue("string", "circle"),
                    validKey to BackupValue("boolean", true)
                ),
                emptyMap()
            )
        )

        assertFalse(preview.settingsToApply.containsKey(invalidKey))
        assertFalse(preview.settingsToApply.containsKey(invalidIconKey))
        assertEquals(BackupValue("boolean", true), preview.settingsToApply[validKey])
        assertTrue(preview.skippedSettings.containsKey(invalidKey))
        assertTrue(preview.skippedSettings.containsKey(invalidIconKey))
        assertEquals(1, preview.warnings.size)
    }

    @Test
    fun `obsolete setting key is skipped`() {
        val obsoleteKey = "removed_preference"
        val preview = validator().buildPreview(
            SettingsBackupData(
                2,
                mapOf(obsoleteKey to BackupValue("string", "legacy-value")),
                emptyMap()
            )
        )

        assertTrue(preview.settingsToApply.isEmpty())
        assertTrue(preview.skippedSettings.containsKey(obsoleteKey))
        assertEquals(1, preview.warnings.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid security setting remains strict`() {
        validator().buildPreview(
            SettingsBackupData(
                2,
                mapOf(
                    SettingsRestoreTestKeys.key(R.string.pref_key_protect_browser) to
                        BackupValue("string", "true")
                ),
                emptyMap()
            )
        )
    }

    @Test
    fun `invalid file sort payload is skipped`() {
        val sortKey = SettingsRestoreTestKeys.key(R.string.pref_key_file_list_sort_options)
        val preview = validator().buildPreview(
            SettingsBackupData(
                2,
                mapOf(
                    sortKey to BackupValue(
                        "file_sort_options_v2",
                        JSONObject()
                            .put("by", "NAME")
                            .put("order", "ASCENDING")
                            .put("isDirectoriesFirst", true)
                            .put("unexpected", "boom")
                    )
                ),
                emptyMap()
            )
        )

        assertFalse(preview.settingsToApply.containsKey(sortKey))
        assertTrue(preview.skippedSettings.containsKey(sortKey))
        assertEquals(1, preview.warnings.size)
    }

    @Test
    fun `legacy file sort parcel is migrated to safe defaults`() {
        val sortKey = SettingsRestoreTestKeys.key(R.string.pref_key_file_list_sort_options)
        val preview = validator().buildPreview(
            SettingsBackupData(
                2,
                mapOf(sortKey to BackupValue("string", "legacy-base64-parcel")),
                emptyMap()
            )
        )

        val migrated = preview.settingsToApply.getValue(sortKey)
        assertEquals("file_sort_options_v2", migrated.type)
        val payload = migrated.value as JSONObject
        assertEquals("NAME", payload.getString("by"))
        assertEquals("ASCENDING", payload.getString("order"))
        assertTrue(payload.getBoolean("isDirectoriesFirst"))
    }

    @Test
    fun `oversized legacy file sort parcel is skipped`() {
        val sortKey = SettingsRestoreTestKeys.key(R.string.pref_key_file_list_sort_options)
        val preview = validator().buildPreview(
            SettingsBackupData(
                2,
                mapOf(
                    sortKey to BackupValue("string", "x".repeat(16_385))
                ),
                emptyMap()
            )
        )

        assertFalse(preview.settingsToApply.containsKey(sortKey))
        assertTrue(preview.skippedSettings.containsKey(sortKey))
        assertEquals(1, preview.warnings.size)
    }

    @Test
    fun `valid value policies pass`() {
        val preview = validator().buildPreview(
            SettingsBackupData(
                2,
                mapOf(
                    SettingsRestoreTestKeys.key(R.string.pref_key_night_mode) to BackupValue("string", "2"),
                    SettingsRestoreTestKeys.key(R.string.pref_key_recycle_bin) to BackupValue("boolean", true),
                    SettingsRestoreTestKeys.key(R.string.pref_key_indexed_search) to BackupValue("boolean", false),
                    SettingsRestoreTestKeys.key(R.string.pref_key_open_apk_default_action) to BackupValue("string", "1"),
                    SettingsRestoreTestKeys.key(R.string.pref_key_file_icon_shape) to
                        BackupValue("string", "rounded_square")
                ),
                emptyMap()
            )
        )

        assertEquals(5, preview.settingsToApply.size)
    }
}
