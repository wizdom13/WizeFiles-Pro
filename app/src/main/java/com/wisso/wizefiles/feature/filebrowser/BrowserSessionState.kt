// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import com.wisso.wizefiles.provider.archive.isArchivePath
import java.nio.file.Path

/**
 * Process-local browser session state.
 *
 * Provider paths are intentionally never persisted here. This only lets an external archive opened
 * while another WizeFiles browser is alive return to that browser's last real directory. Once the
 * last browser activity is destroyed, the path is discarded.
 */
internal object BrowserSessionState {
    private var activeBrowserActivities = 0
    private var lastDirectory: Path? = null

    @Synchronized
    fun hasActiveBrowserActivity(): Boolean = activeBrowserActivities > 0

    @Synchronized
    fun registerBrowserActivity(): Boolean {
        val hadExistingBrowser = activeBrowserActivities > 0
        activeBrowserActivities += 1
        return hadExistingBrowser
    }

    @Synchronized
    fun unregisterBrowserActivity() {
        if (activeBrowserActivities > 0) {
            activeBrowserActivities -= 1
        }
        if (activeBrowserActivities == 0) {
            lastDirectory = null
        }
    }

    @Synchronized
    fun recordDirectory(path: Path) {
        if (!path.isArchivePath) {
            lastDirectory = path
        }
    }

    @Synchronized
    fun returnDirectory(hadExistingBrowser: Boolean, fallback: Path): Path =
        if (hadExistingBrowser) lastDirectory ?: fallback else fallback

    @Synchronized
    internal fun resetForTests() {
        activeBrowserActivities = 0
        lastDirectory = null
    }
}
