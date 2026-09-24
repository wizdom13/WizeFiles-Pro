package com.wisso.wizefiles.feature.filejobs

import java.nio.file.Path

data class BatchRenameOperation(val path: Path, val newName: String)
