// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.transfer

import android.content.Context
import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.storage.RcloneStorage
import com.wisso.wizefiles.util.valueCompat
import java.net.URI

internal data class TransferProgressUi(
    val processedItems: Long,
    val activeItemNumber: Long?,
    val showLiveMetrics: Boolean,
    val forceCompleteProgress: Boolean
)

internal fun TransferOperationRecord.progressUi(): TransferProgressUi =
    buildTransferProgressUi(
        state = state,
        totalItems = totalItems,
        completedItems = completedItems,
        skippedItems = skippedItems,
        failedItems = failedItems,
        hasCurrentItem = currentItem.isNotEmpty()
    )

internal fun buildTransferProgressUi(
    state: TransferOperationState,
    totalItems: Long,
    completedItems: Long,
    skippedItems: Long,
    failedItems: Long,
    hasCurrentItem: Boolean
): TransferProgressUi {
    val processedItems = (completedItems + skippedItems + failedItems)
        .coerceIn(0, totalItems.coerceAtLeast(0))
    val activeItemNumber = if (
        state == TransferOperationState.RUNNING && hasCurrentItem && totalItems > 0
    ) {
        (processedItems + 1).coerceAtMost(totalItems)
    } else {
        null
    }
    return TransferProgressUi(
        processedItems = processedItems,
        activeItemNumber = activeItemNumber,
        showLiveMetrics = state == TransferOperationState.RUNNING,
        forceCompleteProgress = state == TransferOperationState.COMPLETED ||
            state == TransferOperationState.COMPLETED_WITH_WARNINGS
    )
}

internal fun TransferOperationState.displayName(): String = when (this) {
    TransferOperationState.PAUSE_REQUESTED -> "Pausing"
    TransferOperationState.WAITING_FOR_USER -> "Needs attention"
    TransferOperationState.RECOVERABLE -> "Ready to resume"
    TransferOperationState.COMPLETED_WITH_WARNINGS -> "Completed with warnings"
    else -> name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
}

internal fun TransferOperationType.displayName(): String = when (this) {
    TransferOperationType.TWO_WAY_SYNC -> "Two-way sync"
    TransferOperationType.MOVE_BACKUP -> "Move backup"
    TransferOperationType.NEARBY_SEND -> "Nearby send"
    TransferOperationType.NEARBY_RECEIVE -> "Nearby receive"
    TransferOperationType.ARCHIVE_MODIFY -> "Archive update"
    TransferOperationType.APK_SIGN -> "APK signing"
    TransferOperationType.AAB_SIGN -> "AAB signing"
    TransferOperationType.APKS_SIGN -> "APKS signing"
    TransferOperationType.XAPK_SIGN -> "XAPK signing"
    TransferOperationType.APKM_IMPORT -> "APKM import"
    else -> name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
}

internal fun formatTransferPath(context: Context, uriString: String): String {
    val uri = runCatching { URI(uriString) }.getOrNull() ?: return uriString
    return when (uri.scheme?.lowercase()) {
        "rclone" -> {
            val storages = Settings.STORAGES.valueCompat.filterIsInstance<RcloneStorage>()
            formatRcloneTransferUri(uriString) { remoteName ->
                storages.firstOrNull { it.remoteName.equals(remoteName, ignoreCase = true) }
                    ?.getName(context)
            } ?: uriString
        }
        "file" -> formatLocalTransferUri(
            uriString,
            context.getString(com.wisso.wizefiles.R.string.sync_internal_storage)
        ) ?: uriString
        "nearby" -> uri.host?.takeUnless { it == "pending" } ?: "Nearby device"
        else -> uriString
    }
}

internal fun formatLocalTransferUri(
    uriString: String,
    internalStorageName: String
): String? {
    val uri = runCatching { URI(uriString) }.getOrNull() ?: return null
    if (!uri.scheme.equals("file", ignoreCase = true)) return null
    val path = uri.path.orEmpty().replace('\\', '/').trimEnd('/')
    val primaryRoot = "/storage/emulated/0"
    val relative = when {
        path == primaryRoot -> ""
        path.startsWith("$primaryRoot/") -> path.removePrefix("$primaryRoot/")
        else -> return path.trim('/').replace("/", " / ").takeIf(String::isNotBlank)
            ?: internalStorageName
    }
    return if (relative.isBlank()) {
        internalStorageName
    } else {
        "$internalStorageName / ${relative.replace("/", " / ")}"
    }
}

internal fun formatRcloneTransferUri(
    uriString: String,
    remoteDisplayName: (String) -> String?
): String? {
    val uri = runCatching { URI(uriString) }.getOrNull() ?: return null
    if (!uri.scheme.equals("rclone", ignoreCase = true)) return null
    val remoteName = uri.host?.takeIf(String::isNotBlank) ?: return null
    val displayName = remoteDisplayName(remoteName)?.takeIf(String::isNotBlank) ?: remoteName
    val path = uri.path.orEmpty().trim('/').takeIf(String::isNotBlank)
    return if (path == null) displayName else "$displayName / $path"
}
