package com.wisso.wizefiles.feature.filebrowser

internal object FileItemMenuVisibilityPolicy {
    fun shouldShowOpenWith(isDirectory: Boolean, isInRecycleBin: Boolean): Boolean =
        !isDirectory && !isInRecycleBin

    fun shouldShowShare(isDirectory: Boolean, isInRecycleBin: Boolean): Boolean =
        !isDirectory && !isInRecycleBin

    fun shouldShowOpenInTerminalLauncher(isDirectory: Boolean, isInRecycleBin: Boolean): Boolean =
        !isDirectory && !isInRecycleBin
}
