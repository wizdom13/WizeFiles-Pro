// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

internal class BrowserWorkspaceCoordinator {
    var dualPaneVisible: Boolean = false
        private set

    var activePane: BrowserPane = BrowserPane.PRIMARY
        private set

    fun updateVisibility(visible: Boolean, requestedActivePane: BrowserPane): BrowserWorkspaceChange {
        dualPaneVisible = visible
        activePane = if (visible) requestedActivePane else BrowserPane.PRIMARY
        return BrowserWorkspaceChange(
            dualPaneVisible = dualPaneVisible,
            activePane = activePane,
            secondaryPaneRequired = visible
        )
    }

    fun activate(pane: BrowserPane): Boolean {
        if (!dualPaneVisible || activePane == pane) return false
        activePane = pane
        return true
    }

    fun synchronizeActivePane(pane: BrowserPane) {
        activePane = if (dualPaneVisible) pane else BrowserPane.PRIMARY
    }

    fun active(primary: FileListFragment, secondary: FileListFragment?): FileListFragment =
        if (dualPaneVisible && activePane == BrowserPane.SECONDARY) secondary ?: primary
        else primary

    fun other(primary: FileListFragment, secondary: FileListFragment?): FileListFragment? {
        if (!dualPaneVisible) return null
        return when (activePane) {
            BrowserPane.PRIMARY -> secondary
            BrowserPane.SECONDARY -> primary
        }
    }
}

internal data class BrowserWorkspaceChange(
    val dualPaneVisible: Boolean,
    val activePane: BrowserPane,
    val secondaryPaneRequired: Boolean
)
