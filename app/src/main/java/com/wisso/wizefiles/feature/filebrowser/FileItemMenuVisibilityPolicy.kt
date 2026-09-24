// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

internal object FileItemMenuVisibilityPolicy {
    fun shouldShowOpenWith(isDirectory: Boolean, isInRecycleBin: Boolean): Boolean =
        !isDirectory && !isInRecycleBin

    fun shouldShowShare(isDirectory: Boolean, isInRecycleBin: Boolean): Boolean =
        !isDirectory && !isInRecycleBin

    fun shouldShowOpenInTerminalLauncher(isDirectory: Boolean, isInRecycleBin: Boolean): Boolean =
        !isDirectory && !isInRecycleBin
}
