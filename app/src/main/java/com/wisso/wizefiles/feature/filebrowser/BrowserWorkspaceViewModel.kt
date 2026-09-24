package com.wisso.wizefiles.feature.filebrowser

import androidx.lifecycle.ViewModel

/** Window-scoped owner for tabs and their pane layout state. */
internal class BrowserWorkspaceViewModel : ViewModel() {
    val tabsController = BrowserTabsController()
}
