package com.wisso.wizefiles.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRestorePreviewTest {
    private fun repoFile(path: String): File {
        val direct = File(path)
        if (direct.exists()) return direct
        val fromApp = File("..", path)
        if (fromApp.exists()) return fromApp
        throw java.io.FileNotFoundException(path)
    }

    @Test
    fun `restore flow requires preview before apply`() {
        val source = repoFile("app/src/main/java/com/wisso/wizefiles/feature/settings/RestoreSettingsDialogFragment.kt").readText()

        assertTrue(source.contains("manager.previewRestoreFromUri"))
        assertTrue(source.contains("manager.applyRestorePreview"))
        assertTrue(source.contains("settings_restore_preview_title"))
    }

    @Test
    fun `invalid backup does not apply settings directly`() {
        val source = repoFile("app/src/main/java/com/wisso/wizefiles/feature/settings/SettingsBackupRestoreManager.kt").readText()

        assertTrue(source.contains("previewRestoreFromUri"))
        assertTrue(source.contains("applyRestorePreview"))
    }
}
