// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filejobs

import com.wisso.wizefiles.feature.transfer.TransferProgress
import com.wisso.wizefiles.feature.transfer.TransferProgressCheckpoint
import com.wisso.wizefiles.feature.transfer.TransferRepository
import com.wisso.wizefiles.provider.archive.archiveRefresh
import com.wisso.wizefiles.provider.archive.createArchiveRootPath
import com.wisso.wizefiles.provider.archive.editor.ArchiveMutationSpec
import com.wisso.wizefiles.provider.archive.editor.ArchiveRewriteEngine
import com.wisso.wizefiles.storage.path.toAppPathOrNull
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import java.io.IOException

class ArchiveModifyFileOperationJob(
    private val spec: ArchiveMutationSpec
) : FileOperationJob(spec.operationId) {
    @Throws(IOException::class)
    override fun run() {
        beginTransferExecution(spec.mutations.size.toLong(), 0)
        val engine = ArchiveRewriteEngine(
            spec = spec,
            onProgress = { progress ->
                TransferRepository.updatePlanSummary(
                    spec.operationId,
                    spec.mutations.size.toLong(),
                    progress.totalBytes
                )
                val speed = TransferProgress.tracker.sample(
                    spec.operationId,
                    progress.processedBytes,
                    progress.totalBytes
                )
                TransferRepository.checkpoint(
                    TransferProgressCheckpoint(
                        operationId = spec.operationId,
                        itemId = 0,
                        itemBytesCompleted = progress.processedBytes,
                        operationBytesCompleted = progress.processedBytes,
                        currentItem = buildString {
                            append(progress.phase.name.lowercase().replace('_', ' '))
                            if (progress.currentEntry.isNotEmpty()) append(": ${progress.currentEntry}")
                        },
                        speedBytesPerSecond = speed.bytesPerSecond,
                        etaSeconds = speed.etaSeconds
                    )
                )
            },
            checkInterrupted = ::throwIfInterrupted
        )
        engine.execute()
        val archiveFile = spec.archiveUri.toAppPathOrNull()?.toLegacyPathOrNull()
        archiveFile?.createArchiveRootPath()?.archiveRefresh()
        FileOperationService.notifyFileListRefresh()
    }
}
