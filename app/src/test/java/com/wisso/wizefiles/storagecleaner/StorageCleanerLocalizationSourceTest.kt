// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storagecleaner

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageCleanerLocalizationSourceTest {
    @Test
    fun `storage cleaner ui uses string resources for dialog and toast text`() {
        val activitySource = sourceFile("src/main/java/com/wisso/wizefiles/feature/storagecleaner/StorageCleanerActivity.kt")
        val detailsLayout = sourceFile("src/main/res/layout/dialog_storage_cleaner_item_details.xml")
        val repositorySource = sourceFile("src/main/java/com/wisso/wizefiles/feature/storagecleaner/StorageAnalysisRepository.kt")

        assertTrue(activitySource.contains("R.string.storage_cleaner_details_why_flagged"))
        assertTrue(activitySource.contains("R.string.storage_cleaner_open_folder"))
        assertTrue(activitySource.contains("R.string.storage_cleaner_file_missing"))
        assertFalse(activitySource.contains("\"File no longer exists\""))
        assertFalse(activitySource.contains("\"Folder not found\""))
        assertFalse(detailsLayout.contains("Duplicate group members"))

        assertTrue(repositorySource.contains("R.string.storage_cleaner_warning_storage_scan_limited"))
        assertTrue(repositorySource.contains("R.string.storage_cleaner_warning_large_file_analysis_limited"))
        assertTrue(repositorySource.contains("R.string.storage_cleaner_warning_duplicate_analysis_limited"))
        assertTrue(repositorySource.contains("R.string.storage_cleaner_warning_downloads_analysis_limited"))
        assertTrue(repositorySource.contains("R.string.storage_cleaner_warning_junk_analysis_limited"))
        assertTrue(repositorySource.contains("R.string.storage_cleaner_warning_unused_apps_analysis_limited"))
        assertFalse(repositorySource.contains("Storage scan was partially limited"))
        assertFalse(repositorySource.contains("Large file analysis was partially limited"))
        assertFalse(repositorySource.contains("Duplicate analysis was partially limited"))
        assertFalse(repositorySource.contains("Downloads analysis was partially limited"))
        assertFalse(repositorySource.contains("Junk analysis was partially limited"))
        assertFalse(repositorySource.contains("Unused apps analysis was partially limited"))
        assertFalse(activitySource.contains("title.contains(\"download\"") )
        assertFalse(activitySource.contains("reason.contains(\"download\"") )
    }

    private fun sourceFile(path: String): String {
        return File(path).readText()
    }
}
