// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filejobs

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.annotation.AnyRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.android.compat.mainExecutorCompat
import com.wisso.wizefiles.core.files.model.asFileSize
import com.wisso.wizefiles.storage.StorageFacade
import com.wisso.wizefiles.provider.common.readAttributes
import com.wisso.wizefiles.feature.transfer.TransferCenterActivity
import com.wisso.wizefiles.util.createIntent
import com.wisso.wizefiles.util.getQuantityString
import com.wisso.wizefiles.util.showToast
import java.io.IOException

private val storageFacade = StorageFacade()

fun FileOperationJob.getString(@StringRes stringRes: Int): String {
    return service.getString(stringRes)
}

fun FileOperationJob.getString(@StringRes stringRes: Int, vararg formatArguments: Any?): String {
    return service.getString(stringRes, *formatArguments)
}

fun FileOperationJob.getQuantityString(@PluralsRes pluralRes: Int, quantity: Int): String {
    return service.getQuantityString(pluralRes, quantity)
}

fun FileOperationJob.getQuantityString(
    @PluralsRes pluralRes: Int,
    quantity: Int,
    vararg formatArguments: Any?
): String {
    return service.getQuantityString(pluralRes, quantity, *formatArguments)
}

internal fun FileOperationJob.postNotification(
    title: CharSequence,
    text: CharSequence?,
    subText: CharSequence?,
    info: CharSequence?,
    max: Int,
    progress: Int,
    indeterminate: Boolean,
    showCancel: Boolean
) {
    val notification = fileJobNotificationTemplate.createBuilder(service).apply {
        setContentTitle(title)
        setContentText(text)
        setSubText(subText)
        setContentInfo(info)
        setProgress(max, progress, indeterminate)
        val contentIntent = PendingIntent.getActivity(
            service,
            id,
            Intent(service, TransferCenterActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setContentIntent(contentIntent)
        if (showCancel) {
            transferId?.let { operationId ->
                val pauseIntent = PendingIntent.getBroadcast(
                    service,
                    id + 2,
                    FileOperationReceiver.createPauseIntent(operationId),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                addAction(
                    android.R.drawable.ic_media_pause,
                    getString(com.wisso.wizefiles.R.string.transfer_pause),
                    pauseIntent
                )
            }
            val intent = FileOperationReceiver.createIntent(id)
            var pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pendingIntentFlags = pendingIntentFlags or PendingIntent.FLAG_IMMUTABLE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                service, id + 1, intent, pendingIntentFlags
            )
            addAction(
                R.drawable.ic_close_white_24dp, getString(android.R.string.cancel), pendingIntent
            )
        }
    }.build()
    service.notificationManager.notify(id, notification)
}

internal const val PROGRESS_INTERVAL_MILLIS = 200L

internal const val NOTIFICATION_INTERVAL_MILLIS = 500L

internal fun FileOperationJob.showToast(textRes: Int, duration: Int = Toast.LENGTH_SHORT) {
    service.mainExecutorCompat.execute {
        service.showToast(textRes, duration)
    }
}

internal fun FileOperationJob.showToast(text: CharSequence, duration: Int = Toast.LENGTH_SHORT) {
    service.mainExecutorCompat.execute {
        service.showToast(text, duration)
    }
}

internal fun FileOperationJob.notifyFileListRefresh() {
    FileOperationService.notifyFileListRefresh()
}

internal fun FileOperationJob.postCompletionWarningNotification(metadataWarningCount: Int) {
    if (metadataWarningCount <= 0) {
        return
    }
    val text = service.resources.getQuantityString(
        R.plurals.file_job_completion_with_metadata_warning,
        metadataWarningCount,
        metadataWarningCount
    )
    val warningCountText = service.resources.getQuantityString(
        R.plurals.file_job_metadata_warning_count_notification,
        metadataWarningCount,
        metadataWarningCount
    )
    val notification = fileJobNotificationTemplate.createBuilder(service).apply {
        setContentTitle(getString(R.string.file_job_completion_with_warnings_title))
        setContentText(text)
        setSubText(warningCountText)
        setContentInfo(metadataWarningCount.toString())
        setProgress(0, 0, false)
        setAutoCancel(true)
    }.build()
    service.notificationManager.notify(id, notification)
}

internal fun FileOperationJob.reportMetadataWarnings(transferInfo: TransferInfo) {
    reportMetadataWarnings(transferInfo.metadataWarningCount)
}

internal fun FileOperationJob.postScanNotification(scanInfo: ScanInfo, @PluralsRes titleRes: Int) {
    if (!scanInfo.shouldPostNotification()) {
        return
    }
    val size = scanInfo.size.asFileSize().formatHumanReadable(service)
    val fileCount: Int = scanInfo.fileCount
    val title: String = getQuantityString(titleRes, fileCount, fileCount, size)
    postNotification(title, null, null, null, 0, 0, true, true)
}

internal fun FileOperationJob.postTransferSizeNotification(
    transferInfo: TransferInfo,
    currentSource: Path,
    @StringRes titleOneRes: Int,
    @PluralsRes titleMultipleRes: Int
) {
    if (!transferInfo.shouldPostNotification()) {
        return
    }
    val title: String
    val text: String
    val fileCount = transferInfo.fileCount
    val target = transferInfo.target!!
    val size = transferInfo.size
    val transferredSize = transferInfo.transferredSize
    if (fileCount == 1) {
        title = getString(titleOneRes, getFileName(currentSource), getFileName(target))
        val sizeString = size.asFileSize().formatHumanReadable(service)
        val transferredSizeString = transferredSize.asFileSize().formatHumanReadable(service)
        text = getString(
            R.string.file_job_transfer_size_notification_text_one_format, transferredSizeString,
            sizeString
        )
    } else {
        title = getQuantityString(titleMultipleRes, fileCount, fileCount, getFileName(target))
        val currentFileIndex = (transferInfo.transferredFileCount + 1)
            .coerceAtMost(fileCount)
        text = getString(
            R.string.file_job_transfer_size_notification_text_multiple_format, currentFileIndex,
            fileCount
        )
    }
    val max: Int
    val progress: Int
    if (size <= Int.MAX_VALUE) {
        max = size.toInt()
        progress = transferredSize.toInt()
    } else {
        var maxLong = size
        var progressLong = transferredSize
        while (maxLong > Int.MAX_VALUE) {
            maxLong /= 2
            progressLong /= 2
        }
        max = maxLong.toInt()
        progress = progressLong.toInt()
    }
    postNotification(title, text, null, null, max, progress, false, true)
}

internal fun FileOperationJob.postTransferCountNotification(
    transferInfo: TransferInfo,
    currentPath: Path,
    @StringRes titleOneRes: Int,
    @PluralsRes titleMultipleRes: Int
) {
    if (!transferInfo.shouldPostNotification()) {
        return
    }
    val title: String
    val text: String?
    val max: Int
    val progress: Int
    val indeterminate: Boolean
    val fileCount = transferInfo.fileCount
    if (fileCount == 1) {
        title = getString(titleOneRes, getFileName(currentPath))
        text = null
        max = 0
        progress = 0
        indeterminate = true
    } else {
        title = getQuantityString(titleMultipleRes, fileCount, fileCount)
        val transferredFileCount = transferInfo.transferredFileCount
        val currentFileIndex = (transferredFileCount + 1).coerceAtMost(fileCount)
        text = getString(
            R.string.file_job_transfer_count_notification_text_multiple_format, currentFileIndex,
            fileCount
        )
        max = fileCount
        progress = transferredFileCount
        indeterminate = false
    }
    postNotification(title, text, null, null, max, progress, indeterminate, true)
}

internal class TransferInfo(scanInfo: ScanInfo, val target: Path?) {
    var fileCount: Int = scanInfo.fileCount
        private set
    var transferredFileCount = 0
        private set
    var size: Long = scanInfo.size
        private set
    var transferredSize = 0L
        private set
    var metadataWarningCount = 0
        private set

    private var lastNotificationTimeMillis = 0L

    fun incrementTransferredFileCount() {
        ++transferredFileCount
    }

    fun addTransferredFileCount(count: Int) {
        transferredFileCount += count
    }

    fun addTransferredFile(path: Path) {
        ++transferredFileCount
        try {
            transferredSize += path.readAttributes(
                BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS
            ).size()
        } catch (e: IOException) {
            com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
        }
    }

    fun skipFile(path: Path) {
        --fileCount
        try {
            size -= path.readAttributes(
                BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS
            ).size()
        } catch (e: IOException) {
            com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
        }
    }

    fun skipFileIgnoringSize() {
        --fileCount
    }

    fun addToTransferredSize(size: Long) {
        transferredSize += size
    }

    fun recordMetadataWarning(path: Path, exception: IOException) {
        ++metadataWarningCount
        com.wisso.wizefiles.util.AppLog.w(
            "FileJob",
            "Metadata warning for ${path.fileName}: ${exception.message}"
        )
    }

    fun shouldPostNotification(): Boolean {
        val currentTimeMillis = System.currentTimeMillis()
        return if (lastNotificationTimeMillis + NOTIFICATION_INTERVAL_MILLIS < currentTimeMillis) {
            lastNotificationTimeMillis = currentTimeMillis
            true
        } else {
            false
        }
    }
}

enum class CopyMoveType {
    COPY,
    EXTRACT,
    MOVE
}

internal fun CopyMoveType.getResourceId(
    @AnyRes copyRes: Int,
    @AnyRes extractRes: Int,
    @AnyRes moveRes: Int
): Int =
    when (this) {
        CopyMoveType.COPY -> copyRes
        CopyMoveType.EXTRACT -> extractRes
        CopyMoveType.MOVE -> moveRes
    }

internal fun FileOperationJob.postArchiveNotification(transferInfo: TransferInfo, currentFile: Path) {
    postTransferSizeNotification(
        transferInfo, currentFile, R.string.file_job_archive_notification_title_one_format,
        R.plurals.file_job_archive_notification_title_multiple_format
    )
}

internal fun FileOperationJob.postDeleteNotification(transferInfo: TransferInfo, currentPath: Path) {
    postTransferCountNotification(
        transferInfo, currentPath, R.string.file_job_delete_notification_title_one_format,
        R.plurals.file_job_delete_notification_title_multiple_format
    )
}

internal fun FileOperationJob.postWriteNotification(transferInfo: TransferInfo) {
    if (!transferInfo.shouldPostNotification()) {
        return
    }
    val target = transferInfo.target!!
    val title = getString(R.string.file_job_write_notification_title_format, getFileName(target))
    val size = transferInfo.size
    val sizeString = size.asFileSize().formatHumanReadable(service)
    val transferredSize = transferInfo.transferredSize
    val transferredSizeString = transferredSize.asFileSize().formatHumanReadable(service)
    val text = getString(
        R.string.file_job_transfer_size_notification_text_one_format, transferredSizeString,
        sizeString
    )
    val max = size.toInt()
    val progress = transferredSize.toInt()
    postNotification(title, text, null, null, max, progress, false, true)
}
