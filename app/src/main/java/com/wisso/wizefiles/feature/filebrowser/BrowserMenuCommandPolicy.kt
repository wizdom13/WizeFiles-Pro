package com.wisso.wizefiles.feature.filebrowser

import com.wisso.wizefiles.R

/** Stable translation boundary between Android menu resources and browser commands. */
internal object BrowserMenuCommandPolicy {
    enum class Command {
        OPEN_DRAWER, SORT, NEW_TASK, NEW_TAB, TOGGLE_DUAL_PANE, NAVIGATE_UP, NAVIGATE_TO,
        REFRESH, PASTE, RESTORE_ALL, DELETE_ALL, SELECT_ALL, TOGGLE_HIDDEN, SHARE, COPY_PATH,
        OPEN_TERMINAL, ADD_BOOKMARK, CREATE_SHORTCUT, SYNC_PANES
    }

    fun resolve(itemId: Int): Command? = when (itemId) {
        android.R.id.home -> Command.OPEN_DRAWER
        R.id.action_view_sort -> Command.SORT
        R.id.action_new_task -> Command.NEW_TASK
        R.id.action_new_tab -> Command.NEW_TAB
        R.id.action_dual_pane -> Command.TOGGLE_DUAL_PANE
        R.id.action_navigate_up -> Command.NAVIGATE_UP
        R.id.action_navigate_to -> Command.NAVIGATE_TO
        R.id.action_refresh -> Command.REFRESH
        R.id.action_paste -> Command.PASTE
        R.id.action_restore_all -> Command.RESTORE_ALL
        R.id.action_delete_all -> Command.DELETE_ALL
        R.id.action_select_all -> Command.SELECT_ALL
        R.id.action_show_hidden_files -> Command.TOGGLE_HIDDEN
        R.id.action_share -> Command.SHARE
        R.id.action_copy_path -> Command.COPY_PATH
        R.id.action_open_in_terminal -> Command.OPEN_TERMINAL
        R.id.action_add_bookmark -> Command.ADD_BOOKMARK
        R.id.action_create_shortcut -> Command.CREATE_SHORTCUT
        R.id.action_sync_panes -> Command.SYNC_PANES
        else -> null
    }
}
