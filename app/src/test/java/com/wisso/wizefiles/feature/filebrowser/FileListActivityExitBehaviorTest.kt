// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileListActivityExitBehaviorTest {

    @Test
    fun nonRootBack_navigatesNormally_withoutExitDialog() {
        assertFalse(
            FileListActivity.shouldShowExitConfirmation(
                isAtTopOfCurrentStorage = false,
                isPickFlow = false
            )
        )
    }

    @Test
    fun exitPath_showsConfirmation() {
        assertTrue(
            FileListActivity.shouldShowExitConfirmation(
                isAtTopOfCurrentStorage = true,
                isPickFlow = false
            )
        )
    }

    @Test
    fun pickerFlowAtStorageTop_doesNotShowExitConfirmation() {
        assertFalse(
            FileListActivity.shouldShowExitConfirmation(
                isAtTopOfCurrentStorage = true,
                isPickFlow = true
            )
        )
    }

    @Test
    fun confirmExit_finishesActivity() {
        assertTrue(FileListActivity.shouldFinishAfterExitConfirmation(confirmed = true))
    }

    @Test
    fun cancelExit_keepsUserInApp() {
        assertFalse(FileListActivity.shouldFinishAfterExitConfirmation(confirmed = false))
    }

    @Test
    fun regularBrowserFlows_supportTabs() {
        assertTrue(FileListActivity.supportsTabs(Intent.ACTION_VIEW))
        assertTrue(FileListActivity.supportsTabs(null))
    }

    @Test
    fun pickerFlows_remainSingleTab() {
        assertFalse(FileListActivity.supportsTabs(Intent.ACTION_GET_CONTENT))
        assertFalse(FileListActivity.supportsTabs(Intent.ACTION_OPEN_DOCUMENT))
        assertFalse(FileListActivity.supportsTabs(Intent.ACTION_CREATE_DOCUMENT))
        assertFalse(FileListActivity.supportsTabs(Intent.ACTION_OPEN_DOCUMENT_TREE))
    }
}
