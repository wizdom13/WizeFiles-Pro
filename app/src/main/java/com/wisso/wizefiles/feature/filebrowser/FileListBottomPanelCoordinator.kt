package com.wisso.wizefiles.feature.filebrowser

import com.wisso.wizefiles.R

internal object FileListBottomPanelCoordinator {
    data class PastePresentation(
        val titleRes: Int,
        val actionTitleRes: Int,
        val actionIconRes: Int
    )

    sealed interface Content {
        data object Selection : Content

        data class Picker(val mode: PickOptions.Mode) : Content

        data object Paste : Content

        data object Hidden : Content
    }

    fun resolveContent(
        hasSelection: Boolean,
        pickerMode: PickOptions.Mode?,
        hasPasteFiles: Boolean
    ): Content = when {
        hasSelection -> Content.Selection
        pickerMode != null -> Content.Picker(pickerMode)
        hasPasteFiles -> Content.Paste
        else -> Content.Hidden
    }

    fun primarySelectionActionIds(
        selectionCount: Int,
        containsDirectory: Boolean,
        pickerMode: PickOptions.Mode?,
        inRecycleBin: Boolean
    ): List<Int> {
        require(selectionCount > 0) { "Selection actions require at least one file" }
        if (pickerMode != null) {
            return listOf(
                if (pickerMode == PickOptions.Mode.CREATE_FILE) {
                    R.id.action_create
                } else {
                    R.id.action_open
                }
            )
        }
        if (inRecycleBin) {
            return listOf(R.id.action_restore, R.id.action_delete)
        }
        val contextualActionId = when {
            selectionCount == 1 -> R.id.action_rename
            containsDirectory -> R.id.action_select_all
            else -> R.id.action_batch_rename
        }
        return listOf(
            R.id.action_cut,
            R.id.action_copy,
            R.id.action_delete,
            contextualActionId
        )
    }

    fun resolvePastePresentation(
        copy: Boolean,
        containsOnlyArchivePaths: Boolean
    ): PastePresentation = PastePresentation(
        titleRes = when {
            !copy -> R.string.file_list_paste_move_title_format
            containsOnlyArchivePaths -> R.string.file_list_paste_extract_title_format
            else -> R.string.file_list_paste_copy_title_format
        },
        actionTitleRes = if (containsOnlyArchivePaths) {
            R.string.file_list_paste_action_extract_here
        } else {
            R.string.paste
        },
        actionIconRes = if (containsOnlyArchivePaths) {
            R.drawable.ic_extract_control_normal_24dp
        } else {
            R.drawable.ic_paste_control_normal_24dp
        }
    )
}
