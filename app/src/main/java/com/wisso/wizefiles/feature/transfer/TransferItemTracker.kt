// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.transfer

import android.os.SystemClock
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.storage.path.toUriString
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import com.wisso.wizefiles.provider.common.readAttributes
import com.wisso.wizefiles.provider.common.deleteIfExists

internal class TransferItemTracker private constructor(
    private val operationId: String,
    val item: TransferItemRecord,
    private val currentItem: String
) {
    private var bytesCompleted = 0L
    private var lastCheckpointElapsedMillis = 0L

    val isAlreadyFinished: Boolean
        get() = item.state == TransferItemState.COPIED || item.state == TransferItemState.SKIPPED

    fun start(operationTransferredBytes: Long = 0L) {
        checkpoint(operationTransferredBytes)
    }

    fun addBytes(delta: Long, operationTransferredBytes: Long) {
        bytesCompleted += delta.coerceAtLeast(0)
        val now = SystemClock.elapsedRealtime()
        if (now - lastCheckpointElapsedMillis < CHECKPOINT_INTERVAL_MILLIS) return
        lastCheckpointElapsedMillis = now
        checkpoint(operationTransferredBytes)
    }

    fun complete(result: Path, operationTransferredBytes: Long) {
        bytesCompleted = item.sizeBytes.coerceAtLeast(bytesCompleted)
        checkpoint(operationTransferredBytes)
        TransferDatabase.completeItem(item.id, result.toAppPath().toUriString())
    }

    fun prepareTemporaryTarget(finalTarget: Path): Path {
        val suffix = operationId.replace("-", "").take(8)
        val temporary = finalTarget.resolveSibling(".wizefiles-part-$suffix-${item.id}")
        temporary.deleteIfExists()
        TransferDatabase.setTemporaryTarget(item.id, temporary.toAppPath().toUriString())
        return temporary
    }

    fun skip() = TransferDatabase.skipItem(item.id)

    fun fail(throwable: Throwable) = TransferDatabase.failItem(
        item.id,
        throwable.javaClass.simpleName,
        throwable.message.orEmpty()
    )

    private fun checkpoint(operationTransferredBytes: Long) {
        val operation = TransferDatabase.operation(operationId)
        val speed = TransferProgress.tracker.sample(
            operationId,
            operationTransferredBytes,
            operation?.totalBytes ?: 0
        )
        TransferDatabase.checkpoint(
            TransferProgressCheckpoint(
                operationId = operationId,
                itemId = item.id,
                itemBytesCompleted = bytesCompleted,
                operationBytesCompleted = operationTransferredBytes,
                currentItem = currentItem,
                speedBytesPerSecond = speed.bytesPerSecond,
                etaSeconds = speed.etaSeconds
            )
        )
    }

    companion object {
        private const val CHECKPOINT_INTERVAL_MILLIS = 1_500L

        fun begin(operationId: String?, source: Path, target: Path): TransferItemTracker? {
            operationId ?: return null
            val attributes = runCatching {
                source.readAttributes(BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            }.getOrNull()
            val sourceUri = source.toAppPath().toUriString()
            val targetUri = target.toAppPath().toUriString()
            val size = attributes?.size()?.coerceAtLeast(0) ?: 0
            val modified = attributes?.lastModifiedTime()?.toMillis()?.coerceAtLeast(0) ?: 0
            val item = TransferDatabase.beginItem(
                operationId = operationId,
                sourceUri = sourceUri,
                targetUri = targetUri,
                relativePath = source.fileName?.toString().orEmpty(),
                isDirectory = attributes?.isDirectory ?: false,
                sizeBytes = size,
                modifiedMillis = modified,
                sourceFingerprint = "$size:$modified"
            )
            return TransferItemTracker(
                operationId,
                item,
                source.fileName?.toString().orEmpty()
            )
        }
    }
}
