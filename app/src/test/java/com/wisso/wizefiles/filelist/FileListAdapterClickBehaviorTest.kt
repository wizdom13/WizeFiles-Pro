// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import com.wisso.wizefiles.core.files.mime.MimeType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileListAdapterClickBehaviorTest {

    @Test
    fun shouldToggleSelectionOnClick_returnsTrue_whenMultiSelectPickMode() {
        val pickOptions = PickOptions(
            mode = PickOptions.Mode.OPEN_FILE,
            fileName = null,
            readOnly = true,
            mimeTypes = listOf(MimeType.ANY),
            localOnly = false,
            allowMultiple = true,
            allowDirectories = true
        )

        assertTrue(FileListAdapter.shouldToggleSelectionOnClick(false, pickOptions))
    }

    @Test
    fun shouldToggleSelectionOnClick_returnsFalse_whenSingleSelectPickModeAndNoSelection() {
        val pickOptions = PickOptions(
            mode = PickOptions.Mode.OPEN_FILE,
            fileName = null,
            readOnly = true,
            mimeTypes = listOf(MimeType.ANY),
            localOnly = false,
            allowMultiple = false,
            allowDirectories = true
        )

        assertFalse(FileListAdapter.shouldToggleSelectionOnClick(false, pickOptions))
    }

    @Test
    fun shouldToggleSelectionOnClick_returnsTrue_whenSelectionExists() {
        assertTrue(FileListAdapter.shouldToggleSelectionOnClick(true, null))
    }

    @Test
    fun shouldToggleSelectionOnClick_returnsFalse_whenSelectWithLongPressEnabled() {
        val pickOptions = PickOptions(
            mode = PickOptions.Mode.OPEN_FILE,
            fileName = null,
            readOnly = true,
            mimeTypes = listOf(MimeType.ANY),
            localOnly = false,
            allowMultiple = true,
            allowDirectories = true,
            selectWithLongPress = true
        )

        assertFalse(FileListAdapter.shouldToggleSelectionOnClick(false, pickOptions))
        assertFalse(FileListAdapter.shouldToggleSelectionOnClick(true, pickOptions))
    }
}
