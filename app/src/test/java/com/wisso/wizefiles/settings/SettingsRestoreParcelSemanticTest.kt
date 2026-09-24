// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.settings

import android.os.Parcel
import com.wisso.wizefiles.R
import com.wisso.wizefiles.navigation.BookmarkDirectory
import com.wisso.wizefiles.navigation.StandardDirectorySettings
import com.wisso.wizefiles.storage.path.RawAppPath
import com.wisso.wizefiles.util.toBase64
import com.wisso.wizefiles.util.use
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsRestoreParcelSemanticTest {

    private fun validator(
        hasSecurityPassword: Boolean = true,
        hasRootCapability: Boolean = true
    ) = SettingsRestoreValidator(
        context = SettingsRestoreTestKeys.mockContext,
        keySet = SettingsRestoreTestKeys.allKeys,
        hasSecurityPassword = { hasSecurityPassword },
        hasRootCapability = { hasRootCapability },
        keyResolver = { id -> SettingsRestoreTestKeys.key(id) }
    )

    private fun parcelBase64(value: Any?): String {
        val bytes = Parcel.obtain().use { parcel ->
            parcel.writeValue(value)
            parcel.marshall()
        }
        return bytes.toBase64().value
    }

    private fun parcelBase64WithTrailingUnknown(value: Any?): String {
        val bytes = Parcel.obtain().use { parcel ->
            parcel.writeValue(value)
            parcel.writeInt(0x41414141)
            parcel.marshall()
        }
        return bytes.toBase64().value
    }

    @Test
    fun `valid parcel-backed settings pass semantic validation`() {
        val preview = validator().buildPreview(
            SettingsBackupData(
                2,
                mapOf(
                    SettingsRestoreTestKeys.key(R.string.pref_key_file_list_default_directory) to
                        BackupValue("string", parcelBase64(RawAppPath("/storage/emulated/0/Download"))),
                    SettingsRestoreTestKeys.key(R.string.pref_key_standard_directory_settings) to
                        BackupValue(
                            "string",
                            parcelBase64(arrayListOf(StandardDirectorySettings("Downloads", "Downloads", true)))
                        ),
                    SettingsRestoreTestKeys.key(R.string.pref_key_bookmark_directories) to
                        BackupValue(
                            "string",
                            parcelBase64(arrayListOf(BookmarkDirectory("Work", RawAppPath("/storage/emulated/0/Download"))))
                        )
                ),
                secretSettings = mapOf("ignored_secret" to "value")
            )
        )

        assertEquals(3, preview.settingsToApply.size)
        assertTrue(preview.skippedSettings.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parcel with unknown trailing fields is rejected`() {
        validator().buildPreview(
            SettingsBackupData(
                2,
                mapOf(
                    SettingsRestoreTestKeys.key(R.string.pref_key_standard_directory_settings) to
                        BackupValue(
                            "string",
                            parcelBase64WithTrailingUnknown(arrayListOf(StandardDirectorySettings("Downloads", "Downloads", true)))
                        )
                ),
                emptyMap()
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parcel with invalid internal values is rejected`() {
        validator().buildPreview(
            SettingsBackupData(
                2,
                mapOf(
                    SettingsRestoreTestKeys.key(R.string.pref_key_standard_directory_settings) to
                        BackupValue(
                            "string",
                            parcelBase64(arrayListOf(StandardDirectorySettings("../unsafe", "bad", true)))
                        )
                ),
                emptyMap()
            )
        )
    }



    @Test(expected = IllegalArgumentException::class)
    fun `default directory parcel with credentialed uri is rejected`() {
        validator().buildPreview(
            SettingsBackupData(
                2,
                mapOf(
                    SettingsRestoreTestKeys.key(R.string.pref_key_file_list_default_directory) to
                        BackupValue("string", parcelBase64("sftp://user:pass@example.com/storage"))
                ),
                emptyMap()
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `default directory parcel with traversal path is rejected`() {
        validator().buildPreview(
            SettingsBackupData(
                2,
                mapOf(
                    SettingsRestoreTestKeys.key(R.string.pref_key_file_list_default_directory) to
                        BackupValue("string", parcelBase64("/storage/emulated/0/../../data"))
                ),
                emptyMap()
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `default directory parcel with double encoded uri is rejected`() {
        validator().buildPreview(
            SettingsBackupData(
                2,
                mapOf(
                    SettingsRestoreTestKeys.key(R.string.pref_key_file_list_default_directory) to
                        BackupValue("string", parcelBase64("%2566%2569%256c%2565%253a%252f%252f%252fstorage"))
                ),
                emptyMap()
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unsupported default directory parcel type is rejected`() {
        validator().buildPreview(
            SettingsBackupData(
                2,
                mapOf(
                    SettingsRestoreTestKeys.key(R.string.pref_key_file_list_default_directory) to
                        BackupValue("string", parcelBase64(1234))
                ),
                emptyMap()
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `oversized bookmark directory parcel list is rejected`() {
        val bookmarks = ArrayList<BookmarkDirectory>(513)
        repeat(513) { index ->
            bookmarks += BookmarkDirectory(
                "Bookmark$index",
                RawAppPath("/storage/emulated/0/Download/$index")
            )
        }
        validator().buildPreview(
            SettingsBackupData(
                2,
                mapOf(
                    SettingsRestoreTestKeys.key(R.string.pref_key_bookmark_directories) to
                        BackupValue("string", parcelBase64(bookmarks))
                ),
                emptyMap()
            )
        )
    }

    @Test
    fun `preview counts include skipped dangerous settings`() {
        val preview = validator(hasRootCapability = false).buildPreview(
            SettingsBackupData(
                2,
                mapOf(
                    SettingsRestoreTestKeys.key(R.string.pref_key_root_strategy) to BackupValue("string", "2"),
                    SettingsRestoreTestKeys.key(R.string.pref_key_night_mode) to BackupValue("string", "1")
                ),
                emptyMap()
            )
        )

        assertEquals(1, preview.settingsToApply.size)
        assertEquals(1, preview.skippedSettings.size)
        assertEquals(1, preview.warnings.size)
    }
}
