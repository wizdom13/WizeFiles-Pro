package com.wisso.wizefiles.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHandlingSourceTest {
    private fun repoFile(path: String): File {
        val direct = File(path)
        if (direct.exists()) return direct
        val fromApp = File("..", path)
        if (fromApp.exists()) return fromApp
        throw java.io.FileNotFoundException(path)
    }

    @Test
    fun `backup and restore dialogs use char arrays and clear them`() {
        val backup = repoFile("app/src/main/java/com/wisso/wizefiles/feature/settings/BackupSettingsDialogFragment.kt").readText()
        val restore = repoFile("app/src/main/java/com/wisso/wizefiles/feature/settings/RestoreSettingsDialogFragment.kt").readText()

        assertTrue(backup.contains("toCharArray()"))
        assertTrue(backup.contains("password.fill('\\u0000')"))
        assertTrue(restore.contains("toCharArray()"))
        assertTrue(restore.contains("password.fill('\\u0000')"))
    }

    @Test
    fun `touched files do not log password values`() {
        val sources = listOf(
            "SettingsBackupRestoreManager.kt",
            "SettingsBackupModels.kt",
            "SettingsBackupSerializer.kt",
            "SettingsBackupStore.kt",
            "SettingsRestoreValidator.kt"
        ).joinToString("\n") { fileName ->
            repoFile(
                "app/src/main/java/com/wisso/wizefiles/feature/settings/$fileName"
            ).readText()
        }

        assertFalse(sources.contains("Log."))
        assertFalse(sources.contains("println("))
    }
}
