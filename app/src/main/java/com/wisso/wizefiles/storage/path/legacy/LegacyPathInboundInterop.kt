// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage.path

import com.wisso.wizefiles.provider.archive.isArchivePath
import com.wisso.wizefiles.provider.os.isLinuxPath
import java.nio.file.Path

fun Path.toAppPath(): AppPath = when {
    isArchivePath -> RawAppPath(toUri().toString())
    isLinuxPath || isLocalFileSchemePath() -> LocalAppPath(toFile())
    else -> RawAppPath(toUri().toString())
}

private fun Path.isLocalFileSchemePath(): Boolean = runCatching {
    fileSystem.provider().scheme.equals("file", ignoreCase = true)
}.getOrDefault(false)
