package com.wisso.wizefiles.settings

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.feature.filebrowser.FileListActivity
import com.wisso.wizefiles.storage.path.LocalAppPath
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RestoreSettingsAppPickerTest {

    @Test
    fun `restore browse contract opens the WizeFiles browser`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = createRestoreBackupPickerContract().createIntent(
            context,
            restoreBackupPickerMimeTypes()
        )

        assertEquals(FileListActivity::class.java.name, intent.component?.className)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertEquals(
            listOf(MimeType.WIZEFILES_BACKUP.value),
            intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList()
        )
    }

    @Test
    fun `restore manager reads a backup selected as an app path`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val backupFile = File(context.cacheDir, "internal-picker-settings.wzf").apply {
            writeText(
                """
                {
                  "formatVersion":2,
                  "encrypted":false,
                  "settings":{},
                  "secretSettings":{}
                }
                """.trimIndent()
            )
        }
        val store = object : SettingsBackupStore {
            override fun readBackupState(): SettingsBackupData = error("Not used")

            override fun previewRestoreBackupState(data: SettingsBackupData): SettingsRestorePreview =
                SettingsRestorePreview(data.formatVersion, data.settings, emptyMap(), emptyList())

            override fun restorePreview(preview: SettingsRestorePreview) = Unit
        }
        val manager = SettingsBackupRestoreManager(context, store)
        val path = LocalAppPath(backupFile)

        assertFalse(manager.inspectBackupEncryption(path).isEncrypted)
        assertEquals(2, manager.previewRestoreFromPath(path).schemaVersion)
    }
}
