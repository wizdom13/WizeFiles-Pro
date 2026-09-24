// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filejobs

import android.content.Intent
import android.os.Environment
import androidx.annotation.StringRes
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.app.BackgroundActivityStarter
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.provider.legacy.fileProviderUri
import com.wisso.wizefiles.feature.filebrowser.OpenFileAsDialogActivity
import com.wisso.wizefiles.feature.filebrowser.OpenFileAsDialogFragment
import com.wisso.wizefiles.feature.internalviewer.InternalOpenIntents
import com.wisso.wizefiles.feature.mediapreview.MediaPreviewItem
import com.wisso.wizefiles.feature.packageinstaller.PackageInstallerActivity
import com.wisso.wizefiles.storage.StorageFacade
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.provider.archive.isArchivePath
import com.wisso.wizefiles.provider.common.UserActionRequiredException
import com.wisso.wizefiles.provider.common.createDirectories
import com.wisso.wizefiles.provider.common.exists
import com.wisso.wizefiles.provider.common.resolveForeign
import com.wisso.wizefiles.util.createIntent
import com.wisso.wizefiles.util.createViewIntent
import com.wisso.wizefiles.util.extraPath
import com.wisso.wizefiles.util.getQuantityString
import com.wisso.wizefiles.util.putArgs
import com.wisso.wizefiles.util.showToast
import com.wisso.wizefiles.util.withChooser
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException

private val storageFacade = StorageFacade()

class InstallApkJob(private val file: Path) : FileOperationJob() {
    override fun run() {
        open(
            file, R.string.file_install_apk_from_background_title_format,
            R.string.file_install_apk_from_background_text
        ) { file ->
            PackageInstallerActivity.createIntent(
                service,
                file.fileProviderUri,
                file.fileName.toString()
            )
        }
    }
}

class OpenInternalViewerFileOperationJob(
    private val file: Path,
    private val mimeType: MimeType
) : FileOperationJob() {
    override fun run() {
        open(
            file, R.string.file_open_from_background_title_format,
            R.string.file_open_from_background_text
        ) { extractedFile ->
            val extractedPath = extractedFile.toAppPath()
            InternalOpenIntents.create(
                service,
                MediaPreviewItem(extractedPath, mimeType)
            ) ?: extractedFile.fileProviderUri.createViewIntent(mimeType)
                .apply { extraPath = extractedPath }
        }
    }
}

class OpenFileOperationJob(
    private val file: Path,
    private val mimeType: MimeType,
    private val withChooser: Boolean
) : FileOperationJob() {
    override fun run() {
        open(
            file, R.string.file_open_from_background_title_format,
            R.string.file_open_from_background_text
        ) { file ->
            file.fileProviderUri.createViewIntent(mimeType)
                .apply { extraPath = file.toAppPath() }
                .let {
                    if (withChooser) {
                        it.withChooser(
                            OpenFileAsDialogActivity::class.createIntent()
                                .putArgs(OpenFileAsDialogFragment.Args(file.toAppPath()))
                        )
                    } else {
                        it
                    }
                }
        }
    }
}

private val FileOperationJob.cacheDirectory: File
    get() =
        service.externalCacheDir?.takeIf {
            Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED
        } ?: service.cacheDir

@Throws(IOException::class)
private fun FileOperationJob.open(
    file: Path,
    @StringRes notificationTitleFormatRes: Int,
    @StringRes notificationTextRes: Int,
    intentCreator: (Path) -> Intent
) {
    val isExtract = file.isArchivePath
    val scanInfo = scan(
        file, if (isExtract) {
            R.plurals.file_job_extract_scan_notification_title_format
        } else {
            R.plurals.file_job_copy_scan_notification_title_format
        }
    )
    val cacheDirectory = Paths.get(cacheDirectory.path, "open_cache")
    cacheDirectory.createDirectories()
    val targetFileName = getTargetFileName(file)
    val targetFile = cacheDirectory.resolveForeign(targetFileName)
    val transferInfo = TransferInfo(scanInfo, cacheDirectory)
    val actionAllInfo = FileOperationActionAllInfo(replace = true)
    val copied = copy(file, targetFile, isExtract, transferInfo, actionAllInfo)
    if (!copied) {
        return
    }
    BackgroundActivityStarter.startActivity(
        intentCreator(targetFile), getString(notificationTitleFormatRes, targetFileName),
        getString(notificationTextRes), service
    )
}

class RenameFileOperationJob(private val path: Path, private val newName: String) : FileOperationJob() {
    @Throws(IOException::class)
    override fun run() {
        rename(path, newName)
        notifyFileListRefresh()
    }
}

class BatchRenameFileOperationJob(
    private val operations: List<BatchRenameOperation>
) : FileOperationJob() {
    private data class RenameState(
        val originalPath: Path,
        val finalPath: Path,
        var currentPath: Path,
        var rollbackPath: Path? = null
    )

    @Throws(IOException::class)
    override fun run() {
        if (operations.isEmpty()) {
            return
        }
        fun collisionKey(path: Path): String =
            "${path.parent}|${path.fileName.toString().lowercase(java.util.Locale.ROOT)}"
        val sourcePathKeys = operations.map { collisionKey(it.path) }.toSet()
        val finalPaths = operations.map { it.path.resolveSibling(it.newName) }
        if (finalPaths.map(::collisionKey).distinct().size != finalPaths.size) {
            throw IOException("Two files would receive the same name")
        }
        operations.forEach { operation ->
            val finalPath = operation.path.resolveSibling(operation.newName)
            if (collisionKey(finalPath) !in sourcePathKeys && java.nio.file.Files.exists(finalPath)) {
                throw IOException("A file named ${operation.newName} already exists")
            }
        }
        val states = operations.map { operation ->
            val temporaryPath = uniqueTemporarySibling(operation.path, "batch")
            RenameState(
                originalPath = operation.path,
                finalPath = operation.path.resolveSibling(operation.newName),
                currentPath = temporaryPath
            )
        }
        var stagedCount = 0
        var completedCount = 0
        val totalSteps = operations.size * 2
        var completedSteps = 0
        var lastProgressUpdateMillis = 0L
        fun updateProgress(force: Boolean = false) {
            val now = android.os.SystemClock.elapsedRealtime()
            if (force || now - lastProgressUpdateMillis >= PROGRESS_INTERVAL_MILLIS) {
                postNotification(
                    getString(R.string.batch_rename_notification_title),
                    getString(
                        R.string.file_job_transfer_count_notification_text_multiple_format,
                        completedSteps,
                        totalSteps
                    ),
                    null,
                    null,
                    totalSteps,
                    completedSteps,
                    false,
                    true
                )
                lastProgressUpdateMillis = now
            }
        }
        try {
            updateProgress(force = true)
            states.forEach { state ->
                if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
                rename(state.originalPath, state.currentPath.fileName.toString())
                stagedCount++
                completedSteps++
                updateProgress()
            }
            states.forEach { state ->
                if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
                rename(state.currentPath, state.finalPath.fileName.toString())
                state.currentPath = state.finalPath
                completedCount++
                completedSteps++
                updateProgress(completedSteps == totalSteps)
            }
        } catch (exception: Exception) {
            Thread.interrupted()
            rollback(states, stagedCount, completedCount)
            throw exception
        }
        notifyFileListRefresh()
        service.showToast(
            service.resources.getQuantityString(
                R.plurals.batch_rename_completed_count,
                operations.size,
                operations.size
            )
        )
    }

    private fun rollback(states: List<RenameState>, stagedCount: Int, completedCount: Int) {
        val movedStates = states.take(stagedCount)
        movedStates.forEachIndexed { index, state ->
            val expectedCurrentPath = if (index < completedCount) state.finalPath else state.currentPath
            if (java.nio.file.Files.exists(expectedCurrentPath)) {
                val rollbackPath = uniqueTemporarySibling(state.originalPath, "rollback")
                runCatching {
                    rename(expectedCurrentPath, rollbackPath.fileName.toString())
                    state.rollbackPath = rollbackPath
                }
            }
        }
        movedStates.forEach { state ->
            state.rollbackPath?.let { rollbackPath ->
                runCatching { rename(rollbackPath, state.originalPath.fileName.toString()) }
            }
        }
        notifyFileListRefresh()
    }

    private fun uniqueTemporarySibling(path: Path, purpose: String): Path {
        var candidate: Path
        do {
            candidate = path.resolveSibling(
                ".wizefiles-$purpose-${java.util.UUID.randomUUID()}"
            )
        } while (java.nio.file.Files.exists(candidate))
        return candidate
    }
}


@Throws(IOException::class)
private fun FileOperationJob.rename(path: Path, newName: String) {
    val newPath = path.resolveSibling(newName)
    var retry: Boolean
    do {
        retry = false
        try {
            val localRenamed = storageFacade.renameLocal(path, newName)
            if (localRenamed == null) {
                moveAtomically(path, newPath)
            }
        } catch (e: InterruptedIOException) {
            throw e
        } catch (e: IOException) {
            logFileOperationIOException(e)
            if (e is UserActionRequiredException) {
                val result = showUserAction(e)
                if (result) {
                    retry = true
                    continue
                }
            }
            val result = showErrorDialog(
                getString(R.string.file_job_rename_error_title_format, getFileName(path)),
                getString(
                    R.string.file_job_rename_error_message_format, getFileName(newPath),
                    e.toString()
                ),
                getReadOnlyFileStore(path, e),
                false,
                getString(R.string.retry),
                getString(android.R.string.cancel),
                null
            )
            when (result.action) {
                FileOperationErrorAction.POSITIVE -> {
                    retry = true
                    continue
                }
                FileOperationErrorAction.NEGATIVE,
                FileOperationErrorAction.CANCELED -> {
                    requestCancellation()
                    throw InterruptedIOException()
                }
                else -> throw AssertionError(result.action)
            }
        }
    } while (retry)
}
