package com.wisso.wizefiles.feature.filebrowser

import com.wisso.wizefiles.R

/** Converts Android menu IDs into stable, testable browser actions. */
internal object BrowserSelectionActionRouter {
    fun resolve(itemId: Int): BrowserSelectionAction? = when (itemId) {
        R.id.action_cut -> BrowserSelectionAction.Command(BrowserCommand.CUT)
        R.id.action_copy -> BrowserSelectionAction.Command(BrowserCommand.COPY)
        R.id.action_delete -> BrowserSelectionAction.Command(BrowserCommand.DELETE)
        R.id.action_rename -> BrowserSelectionAction.Command(BrowserCommand.RENAME)
        R.id.action_copy_to_other_pane ->
            BrowserSelectionAction.Command(BrowserCommand.COPY_TO_OTHER_PANE)
        R.id.action_move_to_other_pane ->
            BrowserSelectionAction.Command(BrowserCommand.MOVE_TO_OTHER_PANE)
        R.id.action_open -> BrowserSelectionAction.Direct(BrowserDirectSelectionAction.OPEN)
        R.id.action_create -> BrowserSelectionAction.Direct(BrowserDirectSelectionAction.CREATE)
        R.id.action_open_with ->
            BrowserSelectionAction.Direct(BrowserDirectSelectionAction.OPEN_WITH)
        R.id.action_edit -> BrowserSelectionAction.Direct(BrowserDirectSelectionAction.EDIT)
        R.id.action_restore -> BrowserSelectionAction.Direct(BrowserDirectSelectionAction.RESTORE)
        R.id.action_extract -> BrowserSelectionAction.Direct(BrowserDirectSelectionAction.EXTRACT)
        R.id.action_archive -> BrowserSelectionAction.Direct(BrowserDirectSelectionAction.ARCHIVE)
        R.id.action_encrypt -> BrowserSelectionAction.Direct(BrowserDirectSelectionAction.ENCRYPT)
        R.id.action_decrypt -> BrowserSelectionAction.Direct(BrowserDirectSelectionAction.DECRYPT)
        R.id.action_share -> BrowserSelectionAction.Direct(BrowserDirectSelectionAction.SHARE)
        R.id.action_send_nearby ->
            BrowserSelectionAction.Direct(BrowserDirectSelectionAction.SEND_NEARBY)
        R.id.action_batch_rename ->
            BrowserSelectionAction.Direct(BrowserDirectSelectionAction.BATCH_RENAME)
        R.id.action_copy_path ->
            BrowserSelectionAction.Direct(BrowserDirectSelectionAction.COPY_PATH)
        R.id.action_add_bookmark ->
            BrowserSelectionAction.Direct(BrowserDirectSelectionAction.ADD_BOOKMARK)
        R.id.action_create_shortcut ->
            BrowserSelectionAction.Direct(BrowserDirectSelectionAction.CREATE_SHORTCUT)
        R.id.action_properties ->
            BrowserSelectionAction.Direct(BrowserDirectSelectionAction.PROPERTIES)
        R.id.action_select_all ->
            BrowserSelectionAction.Direct(BrowserDirectSelectionAction.SELECT_ALL)
        else -> null
    }
}
