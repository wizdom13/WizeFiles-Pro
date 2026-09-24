// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filejobs

import java.nio.file.Path
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.provider.archive.archiveFile
import com.wisso.wizefiles.provider.archive.createArchiveRootPath
import com.wisso.wizefiles.provider.archive.isArchivePath
import com.wisso.wizefiles.provider.common.getPath
import com.wisso.wizefiles.feature.transfer.OperationControlRegistry
import com.wisso.wizefiles.util.asFileName
import java.io.InterruptedIOException


internal fun FileOperationJob.getFileName(path: Path): String =
    if (path.isAbsolute && path.nameCount == 0) {
        path.fileSystem.separator
    } else {
        path.fileName.toString()
    }

internal fun FileOperationJob.getTargetFileName(source: Path): Path {
    if (source.isArchivePath) {
        val archiveFile = source.archiveFile
        val archiveRoot = archiveFile.toLegacyPathOrNull()?.createArchiveRootPath()
        if (source == archiveRoot) {
            return source.fileSystem.getPath(archiveFile.name.asFileName().baseName)
        }
    }
    return source.fileName
}

@Throws(InterruptedIOException::class)
internal fun FileOperationJob.throwIfInterrupted() {
    OperationControlRegistry.throwIfPauseRequested(transferId)
    if (Thread.interrupted()) {
        throw InterruptedIOException()
    }
}
