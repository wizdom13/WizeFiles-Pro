// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

/**
 * Owns selection transitions and direct-action dispatch. The Fragment provides Android-specific
 * effects, while this coordinator keeps selection rules independently testable.
 */
internal class BrowserSelectionCoordinator(
    private val selection: () -> FileItemSet,
    private val clearSelection: () -> Unit,
    private val dispatchCommand: (BrowserCommand) -> Boolean,
    private val effects: Effects
) {
    data class Effects(
        val open: (FileItemSet) -> Unit,
        val create: (com.wisso.wizefiles.core.files.model.FileItem) -> Unit,
        val openWith: (com.wisso.wizefiles.core.files.model.FileItem) -> Unit,
        val edit: (com.wisso.wizefiles.core.files.model.FileItem) -> Unit,
        val restore: (FileItemSet) -> Unit,
        val extract: (FileItemSet) -> Unit,
        val archive: (FileItemSet) -> Unit,
        val encrypt: (FileItemSet) -> Unit,
        val decrypt: (FileItemSet) -> Unit,
        val share: (FileItemSet) -> Unit,
        val sendNearby: (FileItemSet) -> Unit,
        val batchRename: (FileItemSet) -> Unit,
        val copyPath: (com.wisso.wizefiles.core.files.model.FileItem) -> Unit,
        val addBookmark: (com.wisso.wizefiles.core.files.model.FileItem) -> Unit,
        val createShortcut: (com.wisso.wizefiles.core.files.model.FileItem) -> Unit,
        val properties: (com.wisso.wizefiles.core.files.model.FileItem) -> Unit,
        val selectAll: () -> Unit
    )

    fun perform(action: BrowserSelectionAction): Boolean {
        if (action is BrowserSelectionAction.Command) return dispatchCommand(action.command)
        val files = selection()
        if (!BrowserSelectionPolicy.isAvailable(action, files.size)) return false
        return when ((action as BrowserSelectionAction.Direct).action) {
            BrowserDirectSelectionAction.OPEN -> runEffect { effects.open(files) }
            BrowserDirectSelectionAction.CREATE ->
                files.singleOrNull()?.let { runEffect { effects.create(it) } } ?: false
            BrowserDirectSelectionAction.OPEN_WITH -> single(files, effects.openWith)
            BrowserDirectSelectionAction.EDIT -> single(files, effects.edit)
            BrowserDirectSelectionAction.RESTORE -> runEffect { effects.restore(files) }
            BrowserDirectSelectionAction.EXTRACT -> runEffect { effects.extract(files) }
            BrowserDirectSelectionAction.ARCHIVE -> runEffect { effects.archive(files) }
            BrowserDirectSelectionAction.ENCRYPT -> runEffect { effects.encrypt(files) }
            BrowserDirectSelectionAction.DECRYPT -> runEffect { effects.decrypt(files) }
            BrowserDirectSelectionAction.SHARE -> runEffect { effects.share(files) }
            BrowserDirectSelectionAction.SEND_NEARBY -> runEffect { effects.sendNearby(files) }
            BrowserDirectSelectionAction.BATCH_RENAME -> runEffect { effects.batchRename(files) }
            BrowserDirectSelectionAction.COPY_PATH -> single(files, effects.copyPath)
            BrowserDirectSelectionAction.ADD_BOOKMARK -> single(files, effects.addBookmark)
            BrowserDirectSelectionAction.CREATE_SHORTCUT -> single(files, effects.createShortcut)
            BrowserDirectSelectionAction.PROPERTIES -> single(files, effects.properties)
            BrowserDirectSelectionAction.SELECT_ALL -> runEffect(effects.selectAll)
        }
    }

    private fun single(
        files: FileItemSet,
        effect: (com.wisso.wizefiles.core.files.model.FileItem) -> Unit
    ): Boolean {
        val file = files.singleOrNull() ?: return false
        effect(file)
        clearSelection()
        return true
    }

    private inline fun runEffect(effect: () -> Unit): Boolean {
        effect()
        return true
    }
}
