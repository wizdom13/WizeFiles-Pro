// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import com.wisso.wizefiles.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FileListBottomPanelCoordinatorTest {
    @Test
    fun `selection takes precedence over picker and paste content`() {
        val content = FileListBottomPanelCoordinator.resolveContent(
            hasSelection = true,
            pickerMode = PickOptions.Mode.OPEN_FILE,
            hasPasteFiles = true
        )

        assertSame(FileListBottomPanelCoordinator.Content.Selection, content)
    }

    @Test
    fun `picker takes precedence over paste and preserves its mode`() {
        val content = FileListBottomPanelCoordinator.resolveContent(
            hasSelection = false,
            pickerMode = PickOptions.Mode.OPEN_DIRECTORY,
            hasPasteFiles = true
        )

        assertEquals(
            FileListBottomPanelCoordinator.Content.Picker(PickOptions.Mode.OPEN_DIRECTORY),
            content
        )
    }

    @Test
    fun `paste and hidden modes cover normal browser state`() {
        assertSame(
            FileListBottomPanelCoordinator.Content.Paste,
            FileListBottomPanelCoordinator.resolveContent(
                hasSelection = false,
                pickerMode = null,
                hasPasteFiles = true
            )
        )
        assertSame(
            FileListBottomPanelCoordinator.Content.Hidden,
            FileListBottomPanelCoordinator.resolveContent(
                hasSelection = false,
                pickerMode = null,
                hasPasteFiles = false
            )
        )
    }

    @Test
    fun `normal selection actions preserve contextual fourth action`() {
        assertEquals(
            listOf(
                R.id.action_cut,
                R.id.action_copy,
                R.id.action_delete,
                R.id.action_rename
            ),
            FileListBottomPanelCoordinator.primarySelectionActionIds(
                selectionCount = 1,
                containsDirectory = false,
                pickerMode = null,
                inRecycleBin = false
            )
        )
        assertEquals(
            R.id.action_select_all,
            FileListBottomPanelCoordinator.primarySelectionActionIds(
                selectionCount = 2,
                containsDirectory = true,
                pickerMode = null,
                inRecycleBin = false
            ).last()
        )
        assertEquals(
            R.id.action_batch_rename,
            FileListBottomPanelCoordinator.primarySelectionActionIds(
                selectionCount = 2,
                containsDirectory = false,
                pickerMode = null,
                inRecycleBin = false
            ).last()
        )
    }

    @Test
    fun `picker and recycle bin selections keep their specialized actions`() {
        assertEquals(
            listOf(R.id.action_create),
            FileListBottomPanelCoordinator.primarySelectionActionIds(
                selectionCount = 1,
                containsDirectory = false,
                pickerMode = PickOptions.Mode.CREATE_FILE,
                inRecycleBin = false
            )
        )
        assertEquals(
            listOf(R.id.action_restore, R.id.action_delete),
            FileListBottomPanelCoordinator.primarySelectionActionIds(
                selectionCount = 2,
                containsDirectory = true,
                pickerMode = null,
                inRecycleBin = true
            )
        )
    }

    @Test
    fun `archive copy is presented as extraction`() {
        assertEquals(
            FileListBottomPanelCoordinator.PastePresentation(
                titleRes = R.string.file_list_paste_extract_title_format,
                actionTitleRes = R.string.file_list_paste_action_extract_here,
                actionIconRes = R.drawable.ic_extract_control_normal_24dp
            ),
            FileListBottomPanelCoordinator.resolvePastePresentation(
                copy = true,
                containsOnlyArchivePaths = true
            )
        )
    }

    @Test
    fun `regular copy and move preserve their titles and paste action`() {
        assertEquals(
            FileListBottomPanelCoordinator.PastePresentation(
                titleRes = R.string.file_list_paste_copy_title_format,
                actionTitleRes = R.string.paste,
                actionIconRes = R.drawable.ic_paste_control_normal_24dp
            ),
            FileListBottomPanelCoordinator.resolvePastePresentation(
                copy = true,
                containsOnlyArchivePaths = false
            )
        )
        assertEquals(
            R.string.file_list_paste_move_title_format,
            FileListBottomPanelCoordinator.resolvePastePresentation(
                copy = false,
                containsOnlyArchivePaths = false
            ).titleRes
        )
    }
}
