// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import androidx.lifecycle.ViewModel

/** Window-scoped owner for tabs and their pane layout state. */
internal class BrowserWorkspaceViewModel : ViewModel() {
    val tabsController = BrowserTabsController()
}
