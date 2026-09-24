package com.wisso.wizefiles.provider.archive.legacy

import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import java.nio.file.Path

internal fun Path.toArchiveAppPath(): AppPath = toAppPath()

internal fun AppPath.requireLegacyArchivePath(): Path =
    checkNotNull(toLegacyPathOrNull()) { "Unsupported archive path: $this" }

internal fun AppPath.archivePathString(): String = rawPath
