package com.wisso.wizefiles.feature.filejobs

enum class FileOperationConflictAction {
    MERGE_OR_REPLACE,
    RENAME,
    SKIP,
    CANCEL,
    CANCELED
}
