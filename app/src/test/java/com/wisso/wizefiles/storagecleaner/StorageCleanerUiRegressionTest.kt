// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storagecleaner

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageCleanerUiRegressionTest {

    @Test
    fun `usage access warning is shown once with useful context`() {
        val message =
            "Usage access is required to identify unused apps. Other cleanup results are available."

        val hints = buildStorageCleanerHintLines(
            missingCapabilities = listOf(
                "Grant Usage Access for Unused Apps analysis",
                "Unused apps analysis was partially limited"
            ),
            stateMessage = "Limited results: Grant Usage Access for Unused Apps analysis",
            hasUsageAccess = false,
            usageAccessCapabilityPrefix = "Grant Usage Access",
            usageAccessMessage = message,
            limitedResultsPrefix = "Limited results:"
        )

        assertEquals(listOf(message), hints)
        assertFalse(
            shouldShowStorageCleanerToast(
                "Limited results: Grant Usage Access for Unused Apps analysis",
                "Limited results:"
            )
        )
    }

    @Test
    fun `non-permission scan failures remain visible and toastable`() {
        val hints = buildStorageCleanerHintLines(
            missingCapabilities = emptyList(),
            stateMessage = "Storage scan failed",
            hasUsageAccess = true,
            usageAccessCapabilityPrefix = "Grant Usage Access",
            usageAccessMessage = "Usage access required",
            limitedResultsPrefix = "Limited results:"
        )

        assertEquals(listOf("Storage scan failed"), hints)
        assertTrue(
            shouldShowStorageCleanerToast(
                "Storage scan failed",
                "Limited results:"
            )
        )
    }

    @Test
    fun `usage access button keeps horizontal content margins`() {
        val layout = readProjectFile(
            "src/main/res/layout/activity_storage_cleaner.xml",
            "app/src/main/res/layout/activity_storage_cleaner.xml"
        )
        val buttonId = """android:id="@+id/openUsageAccessButton""""
        assertTrue(layout.contains(buttonId))
        val button = layout.substringAfter(buttonId).substringBefore("/>")

        assertTrue(button.contains("""android:layout_marginStart="12dp""""))
        assertTrue(button.contains("""android:layout_marginEnd="12dp""""))
    }

    @Test
    fun `cleanup screen handles system bars and hides an empty hint view`() {
        val source = readProjectFile(
            "src/main/java/com/wisso/wizefiles/feature/storagecleaner/StorageCleanerActivity.kt",
            "app/src/main/java/com/wisso/wizefiles/feature/storagecleaner/StorageCleanerActivity.kt"
        )

        assertTrue(source.contains("ViewCompat.setOnApplyWindowInsetsListener(root)"))
        assertTrue(source.contains("WindowInsetsCompat.Type.systemBars()"))
        assertTrue(source.contains("ViewCompat.requestApplyInsets(root)"))
        assertTrue(source.contains("binding.permissionHintText.isVisible = hints.isNotEmpty()"))
    }

    @Test
    fun `disabled cleanup actions keep readable state colors`() {
        val source = readProjectFile(
            "src/main/java/com/wisso/wizefiles/feature/storagecleaner/StorageCleanerActivity.kt",
            "app/src/main/java/com/wisso/wizefiles/feature/storagecleaner/StorageCleanerActivity.kt"
        )
        val style = readProjectFile(
            "src/main/res/values/styles.xml",
            "app/src/main/res/values/styles.xml"
        )
        val background = readProjectFile(
            "src/main/res/drawable/bg_storage_cleaner_action_button.xml",
            "app/src/main/res/drawable/bg_storage_cleaner_action_button.xml"
        )
        val textColors = readProjectFile(
            "src/main/res/color/storage_cleaner_action_button_text.xml",
            "app/src/main/res/color/storage_cleaner_action_button_text.xml"
        )

        assertFalse(source.contains("binding.scanButton.alpha"))
        val reviewDeleteButtonState = source
            .substringAfter("binding.reviewDeleteButton.isEnabled =")
            .substringBefore("val recommendations =")
        assertTrue(reviewDeleteButtonState.contains("state.selectedIds.isNotEmpty()"))
        assertTrue(reviewDeleteButtonState.contains("!state.isScanning"))
        assertTrue(reviewDeleteButtonState.contains("!state.isPreparingDeletion"))
        assertTrue(style.contains("@color/storage_cleaner_action_button_text"))
        assertTrue(background.contains("""android:state_enabled="false""""))
        assertTrue(background.contains("?attr/colorSurfaceVariant"))
        assertTrue(textColors.contains("""android:state_enabled="false""""))
        assertTrue(textColors.contains("?attr/colorOnSurfaceVariant"))
    }

    @Test
    fun `cleanup details use structured fields and explicit duplicate member actions`() {
        val activity = readProjectFile(
            "src/main/java/com/wisso/wizefiles/feature/storagecleaner/StorageCleanerActivity.kt",
            "app/src/main/java/com/wisso/wizefiles/feature/storagecleaner/StorageCleanerActivity.kt"
        )
        val dialog = readProjectFile(
            "src/main/res/layout/dialog_storage_cleaner_item_details.xml",
            "app/src/main/res/layout/dialog_storage_cleaner_item_details.xml"
        )
        val fieldRow = readProjectFile(
            "src/main/res/layout/item_storage_cleaner_detail_field.xml",
            "app/src/main/res/layout/item_storage_cleaner_detail_field.xml"
        )
        val duplicateRow = readProjectFile(
            "src/main/res/layout/item_storage_cleaner_duplicate_member.xml",
            "app/src/main/res/layout/item_storage_cleaner_duplicate_member.xml"
        )
        val mapper = readProjectFile(
            "src/main/java/com/wisso/wizefiles/feature/storagecleaner/StorageCleanerItemDetailsMapper.kt",
            "app/src/main/java/com/wisso/wizefiles/feature/storagecleaner/StorageCleanerItemDetailsMapper.kt"
        )

        assertTrue(dialog.contains("""android:id="@+id/detailsSummaryCard""""))
        assertTrue(dialog.contains("""android:id="@+id/detailsContextCard""""))
        assertTrue(dialog.contains("""android:id="@+id/detailsFieldsCard""""))
        assertTrue(fieldRow.contains("""android:id="@+id/detailFieldLabel""""))
        assertTrue(fieldRow.contains("""android:id="@+id/detailFieldValue""""))
        assertTrue(duplicateRow.contains("""android:id="@+id/duplicateMemberPreview""""))
        assertTrue(duplicateRow.contains("""android:id="@+id/duplicateMemberOpenFolder""""))
        assertTrue(duplicateRow.contains("""android:id="@+id/duplicateMemberOpen""""))
        assertTrue(activity.contains("R.layout.item_storage_cleaner_detail_field"))
        assertTrue(activity.contains("R.layout.item_storage_cleaner_duplicate_member"))
        assertTrue(activity.contains("bindDuplicateMemberPreview(preview, member.path)"))
        assertFalse(mapper.contains("""StorageCleanerItemDetailsField("Category""""))
        assertFalse(mapper.contains("""StorageCleanerItemDetailsField("Reclaimable size""""))
        assertFalse(mapper.contains("""StorageCleanerItemDetailsField("Flag reason""""))
        assertFalse(mapper.contains("""StorageCleanerItemDetailsField("Duplicate group""""))
        assertFalse(mapper.contains("""StorageCleanerItemDetailsField("Keep this file""""))
        assertFalse(mapper.contains("Selecting this item includes it in cleanup"))
        assertFalse(mapper.contains("Review app details before cleanup"))
        assertTrue(mapper.contains("R.string.storage_cleaner_details_selection_impact"))
    }

    private fun readProjectFile(vararg candidates: String): String {
        val file = candidates.map(::File).firstOrNull { it.exists() }
            ?: error("Unable to locate any of: ${candidates.joinToString()}")
        return file.readText()
    }
}
