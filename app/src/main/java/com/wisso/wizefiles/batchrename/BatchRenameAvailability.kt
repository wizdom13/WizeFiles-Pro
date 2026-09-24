package com.wisso.wizefiles.batchrename

object BatchRenameAvailability {
    fun isAvailable(
        selectedCount: Int,
        containsDirectory: Boolean,
        containsUnsupportedPath: Boolean,
        isReadOnly: Boolean,
        containsArchivePath: Boolean,
        isInRecycleBin: Boolean
    ): Boolean =
        selectedCount >= 2 &&
            !containsDirectory &&
            !containsUnsupportedPath &&
            !isReadOnly &&
            !containsArchivePath &&
            !isInRecycleBin
}
