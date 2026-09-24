package com.wisso.wizefiles.feature.filejobs

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import com.wisso.wizefiles.R
import com.wisso.wizefiles.storage.StorageFacade
import com.wisso.wizefiles.storage.archive.ArchiveTreeTraverser
import com.wisso.wizefiles.storage.local.LocalFileNode
import com.wisso.wizefiles.storage.local.LocalTreeTraverser
import com.wisso.wizefiles.storage.local.LocalPathNode
import com.wisso.wizefiles.storage.local.runLocalMutation
import com.wisso.wizefiles.storage.local.threadInterruptionCancellation
import com.wisso.wizefiles.storage.FileOperationRequest
import com.wisso.wizefiles.provider.archive.archiveFile
import com.wisso.wizefiles.provider.archive.archiver.ArchiveWriter
import com.wisso.wizefiles.provider.archive.isArchivePath
import com.wisso.wizefiles.provider.document.isDocumentPath
import com.wisso.wizefiles.provider.ftp.isFtpPath
import com.wisso.wizefiles.provider.smb.isSmbPath
import com.wisso.wizefiles.provider.sftp.isSftpPath
import com.wisso.wizefiles.provider.common.ByteString
import com.wisso.wizefiles.provider.common.ByteStringBuilder
import com.wisso.wizefiles.provider.common.ByteStringListPath
import com.wisso.wizefiles.provider.common.createFile
import com.wisso.wizefiles.provider.common.deleteIfExists
import com.wisso.wizefiles.provider.common.exists
import com.wisso.wizefiles.provider.common.isDirectory
import com.wisso.wizefiles.provider.common.newByteChannel
import com.wisso.wizefiles.provider.common.resolveForeign
import com.wisso.wizefiles.provider.common.toByteString
import com.wisso.wizefiles.provider.os.isLinuxPath
import com.wisso.wizefiles.provider.rclone.completeRcloneDelete
import com.wisso.wizefiles.provider.rclone.rollbackRcloneDeletes
import com.wisso.wizefiles.provider.rclone.stageRcloneDeletes
import com.wisso.wizefiles.provider.rclone.RcloneUploadPlanEntry
import com.wisso.wizefiles.provider.rclone.completeRcloneUploadPlan
import com.wisso.wizefiles.provider.rclone.markRcloneUploadCopying
import com.wisso.wizefiles.provider.rclone.planRcloneUploads
import com.wisso.wizefiles.provider.rclone.removeRcloneUploadPlans
import com.wisso.wizefiles.recyclebin.RecycleBinManager
import com.wisso.wizefiles.feature.transfer.TransferItemTracker
import com.wisso.wizefiles.core.fastops.FastFileOps
import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.util.asFileName
import com.wisso.wizefiles.util.valueCompat
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import java.util.UUID

private val storageFacade = StorageFacade()

class DeleteFileOperationJob(
    private val paths: List<Path>,
    private val options: DeleteOptions = DeleteOptions(),
    transferId: String? = null
) : FileOperationJob(transferId) {
    @Throws(IOException::class)
    override fun run() {
        beginTransferExecution(paths.size.toLong(), 0L)
        val pendingDeletes = paths.mapNotNull { path ->
            val tracker = TransferItemTracker.begin(transferId, path, path)
            if (tracker?.isAlreadyFinished == true) {
                return@mapNotNull null
            }
            try {
                Files.readAttributes(
                    path,
                    BasicFileAttributes::class.java,
                    LinkOption.NOFOLLOW_LINKS
                )
            } catch (_: NoSuchFileException) {
                tracker?.complete(path, 0L)
                return@mapNotNull null
            }
            path to tracker
        }
        val pendingPaths = pendingDeletes.map { it.first }
        stageRcloneDeletes(pendingPaths)
        try {
            val recycleBinEnabled = Settings.RECYCLE_BIN.valueCompat
            val (pathsToRecycleLocally, pathsToDeleteThroughProvider) =
                pendingDeletes.partition { (path, _) ->
                    DeleteTargetPolicy.shouldUseLocalRecycleBin(
                        path,
                        options,
                        recycleBinEnabled
                    )
                }
            if (pathsToRecycleLocally.isNotEmpty()) {
                recycle(pathsToRecycleLocally)
            }
            if (pathsToDeleteThroughProvider.isNotEmpty()) {
                val scanInfo = scan(
                    pathsToDeleteThroughProvider.map { it.first },
                    R.plurals.file_job_delete_scan_notification_title_format,
                    FileOperationType.DELETE
                )
                val transferInfo = TransferInfo(scanInfo, null)
                val actionAllInfo = FileOperationActionAllInfo()
                for ((path, tracker) in pathsToDeleteThroughProvider) {
                    tracker?.start()
                    try {
                        deleteRecursively(path, transferInfo, actionAllInfo)
                        tracker?.complete(path, 0L)
                    } catch (throwable: Throwable) {
                        tracker?.fail(throwable)
                        throw throwable
                    }
                    throwIfInterrupted()
                }
            }
            notifyFileListRefresh()
        } finally {
            // Successful rclone deletes remove their tombstones in the provider. Any entries
            // left here failed, were skipped, or were cancelled and must become visible again.
            rollbackRcloneDeletes(pendingPaths)
        }
    }

    @Throws(IOException::class)
    private fun recycle(paths: List<Pair<Path, TransferItemTracker?>>) {
        for ((path, tracker) in paths) {
            tracker?.start()
            try {
                if (RecycleBinManager.isRecycleBinPath(path)) {
                    deleteRecursively(path, null, FileOperationActionAllInfo())
                } else {
                    RecycleBinManager.moveToRecycleBin(path)
                    completeRcloneDelete(path)
                }
                tracker?.complete(path, 0L)
            } catch (throwable: Throwable) {
                tracker?.fail(throwable)
                throw throwable
            }
            throwIfInterrupted()
        }
    }

    @Throws(IOException::class)
    private fun deleteRecursively(
        path: Path,
        transferInfo: TransferInfo?,
        actionAllInfo: FileOperationActionAllInfo
    ) {
        if (path.isLinuxPath) {
            deleteRecursivelyLocal(path, transferInfo, actionAllInfo)
            return
        }
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            @Throws(IOException::class)
            override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                delete(file, transferInfo, actionAllInfo, options.secureShred)
                throwIfInterrupted()
                return FileVisitResult.CONTINUE
            }

            @Throws(IOException::class)
            override fun visitFileFailed(file: Path, exception: IOException): FileVisitResult {
                var retryCount = 0
                var currentException = exception!!
                while (true) {
                    val decision = resolveTreeFailure(
                        operationType = FileOperationType.DELETE,
                        phase = FileOperationPhase.EXECUTE,
                        path = file,
                        exception = currentException,
                        allowSkip = true,
                        retryCount = retryCount,
                        dialogSpec = FileOperationErrorDialogSpec(
                            title = getString(R.string.file_job_delete_error_title),
                            message = getString(
                                R.string.file_job_delete_error_message_format,
                                getFileName(file),
                                currentException.toString()
                            ),
                            readOnlyFileStore = getReadOnlyFileStore(file, currentException),
                            showAll = true,
                            positiveButtonText = getString(R.string.retry),
                            negativeButtonText = getString(R.string.skip),
                            neutralButtonText = getString(android.R.string.cancel)
                        ),
                        onSkip = { transferInfo?.skipFileIgnoringSize() }
                    )
                    if (decision != FileOperationRetryDecision.RETRY) {
                        return FileVisitResult.CONTINUE
                    }
                    if (retryCount >= FileOperationStatePolicy.MAX_RETRY_ATTEMPTS) {
                        throw InterruptedIOException().apply { initCause(currentException) }
                    }
                    ++retryCount
                    try {
                        delete(file, transferInfo, actionAllInfo, options.secureShred)
                        throwIfInterrupted()
                        return FileVisitResult.CONTINUE
                    } catch (e: IOException) {
                        currentException = e
                    }
                }
            }

            @Throws(IOException::class)
            override fun postVisitDirectory(
                directory: Path,
                exception: IOException?
            ): FileVisitResult {
                if (exception != null) {
                    var retryCount = 0
                    var currentException = exception!!
                    while (true) {
                        val decision = resolveTreeFailure(
                            operationType = FileOperationType.DELETE,
                            phase = FileOperationPhase.FINALIZE,
                            path = directory,
                            exception = currentException,
                            allowSkip = true,
                            retryCount = retryCount,
                            dialogSpec = FileOperationErrorDialogSpec(
                                title = getString(R.string.file_job_delete_error_title),
                                message = getString(
                                    R.string.file_job_delete_error_message_format,
                                    getFileName(directory),
                                    currentException.toString()
                                ),
                                readOnlyFileStore = getReadOnlyFileStore(directory, currentException),
                                showAll = true,
                                positiveButtonText = getString(R.string.retry),
                                negativeButtonText = getString(R.string.skip),
                                neutralButtonText = getString(android.R.string.cancel)
                            ),
                            onSkip = { transferInfo?.skipFileIgnoringSize() }
                        )
                        if (decision != FileOperationRetryDecision.RETRY) {
                            return FileVisitResult.CONTINUE
                        }
                        if (retryCount >= FileOperationStatePolicy.MAX_RETRY_ATTEMPTS) {
                            throw InterruptedIOException().apply { initCause(currentException) }
                        }
                        ++retryCount
                        try {
                            delete(directory, transferInfo, actionAllInfo, options.secureShred)
                            throwIfInterrupted()
                            return FileVisitResult.CONTINUE
                        } catch (e: IOException) {
                            currentException = e
                        }
                    }
                }
                delete(directory, transferInfo, actionAllInfo, options.secureShred)
                throwIfInterrupted()
                return FileVisitResult.CONTINUE
            }
        })
    }

    @Throws(IOException::class)
    private fun deleteRecursivelyLocal(
        path: Path,
        transferInfo: TransferInfo?,
        actionAllInfo: FileOperationActionAllInfo
    ) {
        val deletedCount = runCatching { FastFileOps.deleteLocalTree(path, options.secureShred) }.getOrNull()
        if (deletedCount != null) {
            if (transferInfo != null && deletedCount > 0) {
                transferInfo.addTransferredFileCount(deletedCount)
                postDeleteNotification(transferInfo, path)
            }
            return
        }
        val localNodes = LocalTreeTraverser.walkPostOrder(LocalFileNode(path.toFile()))
        for (node in localNodes) {
            delete(Paths.get(node.file.path), transferInfo, actionAllInfo, options.secureShred)
            throwIfInterrupted()
        }
    }
}

@Throws(IOException::class)
internal fun FileOperationJob.delete(
    path: Path,
    transferInfo: TransferInfo?,
    actionAllInfo: FileOperationActionAllInfo,
    secureShred: Boolean = false
) {
    runWithRetryPolicy(
        block = {
            if (secureShred) {
                shredFileIfSupported(path)
            }
            if (path.isLinuxPath && !secureShred) {
                runLocalMutation(
                    FileOperationRequest.Delete(LocalPathNode(path)),
                    cancellation = threadInterruptionCancellation
                )
            } else {
                storageFacade.delete(path)
            }
            if (transferInfo != null) {
                transferInfo.incrementTransferredFileCount()
                postDeleteNotification(transferInfo, path)
            }
        },
        onIOException = { e, _ ->
            if (actionAllInfo.skipDeleteError) {
                if (transferInfo != null) {
                    transferInfo.skipFileIgnoringSize()
                    postDeleteNotification(transferInfo, path)
                }
                return@runWithRetryPolicy FileOperationRetryDecision.COMPLETE
            }
            resolveRetrySkipOrCancel(
                path,
                e,
                FileOperationErrorDialogSpec(
                    title = getString(R.string.file_job_delete_error_title),
                    message = getString(
                        R.string.file_job_delete_error_message_format,
                        getFileName(path),
                        e.toString()
                    ),
                    readOnlyFileStore = getReadOnlyFileStore(path, e),
                    showAll = true,
                    positiveButtonText = getString(R.string.retry),
                    negativeButtonText = getString(R.string.skip),
                    neutralButtonText = getString(android.R.string.cancel)
                ),
                onSkip = {
                    if (transferInfo != null) {
                        transferInfo.skipFileIgnoringSize()
                        postDeleteNotification(transferInfo, path)
                    }
                },
                onSkipAll = { actionAllInfo.skipDeleteError = true }
            )
        }
    )
}

@Throws(IOException::class)
private fun shredFileIfSupported(path: Path) {
    if (!DeleteOptionsSupport.supportsSecureShred(path)) {
        return
    }
    RandomAccessFile(path.toFile(), "rw").use { file ->
        val zeroBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var remaining = file.length()
        file.seek(0L)
        while (remaining > 0L) {
            val writeSize = minOf(zeroBuffer.size.toLong(), remaining).toInt()
            file.write(zeroBuffer, 0, writeSize)
            remaining -= writeSize
        }
        file.fd.sync()
    }
}
