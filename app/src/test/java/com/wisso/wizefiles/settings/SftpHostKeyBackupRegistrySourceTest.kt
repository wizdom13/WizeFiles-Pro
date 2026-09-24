// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SftpHostKeyBackupRegistrySourceTest {
    @Test
    fun `backup registry includes sftp pinned host key setting`() {
        val source = sourceFile("src/main/java/com/wisso/wizefiles/feature/settings/SettingsBackupModels.kt")

        assertTrue(source.contains("R.string.pref_key_sftp_pinned_host_keys"))
    }

    private fun sourceFile(relativePath: String): String =
        File("$PROJECT_ROOT/$relativePath").readText()

    companion object {
        private const val PROJECT_ROOT = "."
    }
}
