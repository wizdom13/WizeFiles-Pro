// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupViewIntentDetectionTest {

    @Test
    fun `double-encoded nested uri payload is rejected`() {
        assertFalse(
            SettingsBackupViewIntent.isBackupCandidateValue(
                "file%253A%252F%252F%252Fstorage%252Femulated%252F0%252FDownload%252FWizeFiles_backup08042026.wzf"
            )
        )
    }

    @Test
    fun `display name fallback supports backup detection`() {
        assertTrue(
            SettingsBackupViewIntent.matchesBackupCandidates(
                listOf("content://provider/doc/1234", "WizeFiles_backup08042026.wzf")
            )
        )
    }

    @Test
    fun `non backup names are not matched`() {
        assertFalse(SettingsBackupViewIntent.isBackupCandidateValue("content://provider/doc/readme.txt"))
    }
}
