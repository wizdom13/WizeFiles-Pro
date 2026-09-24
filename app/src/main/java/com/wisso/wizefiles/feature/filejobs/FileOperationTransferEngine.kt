// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filejobs

import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.file.CopyOption
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import com.wisso.wizefiles.R
import com.wisso.wizefiles.storage.StorageFacade
import com.wisso.wizefiles.storage.archive.ArchiveTreeTraverser
import com.wisso.wizefiles.storage.local.LocalFileNode
import com.wisso.wizefiles.storage.local.LocalTreeTraverser
import com.wisso.wizefiles.storage.local.LocalMutationOptions
import com.wisso.wizefiles.storage.local.LocalPathNode
import com.wisso.wizefiles.storage.local.runLocalMutation
import com.wisso.wizefiles.storage.local.threadInterruptionCancellation
import com.wisso.wizefiles.storage.FileOperationRequest
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.provider.archive.isArchivePath
import com.wisso.wizefiles.provider.common.InvalidFileNameException
import com.wisso.wizefiles.provider.common.ProgressCopyOption
import com.wisso.wizefiles.provider.common.MetadataPreservationCopyOption
import com.wisso.wizefiles.provider.common.UserActionRequiredException
import com.wisso.wizefiles.provider.common.copyTo
import com.wisso.wizefiles.provider.common.existsInCommittedStorage
import com.wisso.wizefiles.provider.common.isDirectory
import com.wisso.wizefiles.provider.common.moveTo
import com.wisso.wizefiles.provider.common.resolveForeign
import com.wisso.wizefiles.provider.os.isLinuxPath
import com.wisso.wizefiles.provider.rclone.moveRcloneUploadPlan
import com.wisso.wizefiles.feature.transfer.TransferItemTracker
import com.wisso.wizefiles.feature.transfer.TransferRepository

private const val TRANSFER_PROGRESS_INTERVAL_MILLIS = 200L

private val transferStorageFacade = StorageFacade()

private interface FileOperationTransferBackend {
    @Throws(IOException::class)
    fun transfer(source: Path, target: Path, options: Array<CopyOption>)
}

private object StorageCopyTransferBackend : FileOperationTransferBackend {
    @Throws(IOException::class)
    override fun transfer(source: Path, target: Path, options: Array<CopyOption>) {
        transferStorageFacade.copy(source, target, *options)
    }
}

private object StorageMoveTransferBackend : FileOperationTransferBackend {
    @Throws(IOException::class)
    override fun transfer(source: Path, target: Path, options: Array<CopyOption>) {
        transferStorageFacade.move(source, target, *options)
    }
}

private object ArchiveCopyTransferBackend : FileOperationTransferBackend {
    @Throws(IOException::class)
    override fun transfer(source: Path, target: Path, options: Array<CopyOption>) {
        source.copyTo(target, *options)
    }
}

private fun selectTransferBackend(
    useCopy: Boolean,
    source: Path,
    target: Path
): FileOperationTransferBackend = when {
    !useCopy -> StorageMoveTransferBackend
    source.isArchivePath || target.isArchivePath -> ArchiveCopyTransferBackend
    else -> StorageCopyTransferBackend
}

@Throws(IOException::class)
internal fun FileOperationJob.copy(
    source: Path,
    target: Path,
    isExtract: Boolean,
    transferInfo: TransferInfo,
    actionAllInfo: FileOperationActionAllInfo
): Boolean =
    copyOrMove(
        source,
        target,
        if (isExtract) CopyMoveType.EXTRACT else CopyMoveType.COPY,
        true,
        false,
        transferInfo,
        actionAllInfo
    )

@Throws(IOException::class)
internal fun FileOperationJob.copyForMove(
    source: Path,
    target: Path,
    transferInfo: TransferInfo,
    actionAllInfo: FileOperationActionAllInfo
): Boolean = copyOrMove(source, target, CopyMoveType.MOVE, true, true, transferInfo, actionAllInfo)

@Throws(IOException::class)
internal fun FileOperationJob.moveAtomically(source: Path, target: Path) {
    val trackedItem = TransferItemTracker.begin(transferId, source, target)
    if (trackedItem?.isAlreadyFinished == true) return
    if (source.isLinuxPath && target.isLinuxPath) {
        runLocalMutation(
            FileOperationRequest.Move(LocalPathNode(source), LocalPathNode(target)),
            LocalMutationOptions(atomicMove = true),
            threadInterruptionCancellation
        )
    } else {
        source.moveTo(target, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.ATOMIC_MOVE)
    }
    trackedItem?.complete(target, 0L)
}

@Throws(IOException::class)
internal fun FileOperationJob.moveByCopy(
    source: Path,
    target: Path,
    transferInfo: TransferInfo,
    actionAllInfo: FileOperationActionAllInfo
): Boolean =
    copyOrMove(source, target, CopyMoveType.MOVE, false, true, transferInfo, actionAllInfo)

@Throws(IOException::class)
internal fun FileOperationJob.copyOrMove(
    source: Path,
    target: Path,
    type: CopyMoveType,
    useCopy: Boolean,
    copyAttributes: Boolean,
    transferInfo: TransferInfo,
    actionAllInfo: FileOperationActionAllInfo
): Boolean {
    val targetParent = target.parent
    if (targetParent.startsWith(source)) {
        when (
            resolveCopyMoveStructuralConflict(
                source,
                target,
                type,
                actionAllInfo,
                FileOperationStructuralConflictKind.INTO_SELF
            )
        ) {
            FileOperationStructuralConflictResolution.Continue -> Unit
            FileOperationStructuralConflictResolution.Skip -> {
                transferInfo.skipFile(source)
                postCopyMoveNotification(transferInfo, source, type)
                return false
            }
            FileOperationStructuralConflictResolution.Interrupt -> throw InterruptedIOException()
        }
    }
    if (source.startsWith(target)) {
        when (
            resolveCopyMoveStructuralConflict(
                source,
                target,
                type,
                actionAllInfo,
                FileOperationStructuralConflictKind.OVER_SELF
            )
        ) {
            FileOperationStructuralConflictResolution.Continue -> Unit
            FileOperationStructuralConflictResolution.Skip -> {
                transferInfo.skipFile(source)
                postCopyMoveNotification(transferInfo, source, type)
                return false
            }
            FileOperationStructuralConflictResolution.Interrupt -> throw InterruptedIOException()
        }
    }
    val trackedItem = TransferItemTracker.begin(transferId, source, target)
    if (trackedItem?.isAlreadyFinished == true) {
        transferInfo.addTransferredFile(target)
        return true
    }
    val backend = selectTransferBackend(useCopy, source, target)
    var resolvedTarget = target
    var replaceExisting = false
    var retryCount = 0
    while (true) {
        val options = mutableListOf<CopyOption>().apply {
            this += LinkOption.NOFOLLOW_LINKS
            if (copyAttributes) {
                this += StandardCopyOption.COPY_ATTRIBUTES
                this += MetadataPreservationCopyOption { report ->
                    report.warnings.forEach { warning ->
                        transferInfo.recordMetadataWarning(
                            resolvedTarget,
                            IOException(
                                "${warning.attribute}: ${warning.detail ?: warning.status.name}"
                            )
                        )
                    }
                }
            }
            if (replaceExisting) {
                this += StandardCopyOption.REPLACE_EXISTING
            }
            this += ProgressCopyOption(TRANSFER_PROGRESS_INTERVAL_MILLIS) {
                transferInfo.addToTransferredSize(it)
                trackedItem?.addBytes(it, transferInfo.transferredSize)
                postCopyMoveNotification(transferInfo, source, type)
            }
        }.toTypedArray()
        try {
            postCopyMoveNotification(transferInfo, source, type)
            if (
                !replaceExisting &&
                resolvedTarget.existsInCommittedStorage(LinkOption.NOFOLLOW_LINKS)
            ) {
                throw FileAlreadyExistsException(resolvedTarget.toString())
            }
            val transferTarget = if (
                useCopy && trackedItem != null && !trackedItem.item.isDirectory
            ) {
                trackedItem.prepareTemporaryTarget(resolvedTarget)
            } else {
                resolvedTarget
            }
            backend.transfer(source, transferTarget, options)
            if (transferTarget != resolvedTarget) {
                val finalizeOptions = if (replaceExisting) {
                    arrayOf<CopyOption>(StandardCopyOption.REPLACE_EXISTING)
                } else {
                    emptyArray()
                }
                transferTarget.moveTo(resolvedTarget, *finalizeOptions)
            }
            transferInfo.incrementTransferredFileCount()
            trackedItem?.complete(resolvedTarget, transferInfo.transferredSize)
            postCopyMoveNotification(transferInfo, source, type)
            return true
        } catch (e: FileAlreadyExistsException) {
            val activeTransferId = transferId
            val decisionId = activeTransferId?.let {
                TransferRepository.beginDecision(
                    it,
                    trackedItem?.item?.id ?: 0,
                    "TARGET_EXISTS",
                    "${source} -> ${resolvedTarget}",
                    "replace,rename,merge,skip,cancel"
                )
            }
            val resolution = resolveTargetAlreadyExistsConflict(
                source,
                resolvedTarget,
                type,
                actionAllInfo,
                e
            )
            if (decisionId != null) {
                TransferRepository.resolveDecision(
                    activeTransferId,
                    decisionId,
                    resolution.javaClass.simpleName,
                    false
                )
            }
            when (resolution) {
                FileOperationTargetConflictResolution.MergeIntoDirectory -> {
                    transferInfo.addTransferredFile(resolvedTarget)
                    trackedItem?.complete(resolvedTarget, transferInfo.transferredSize)
                    postCopyMoveNotification(transferInfo, source, type)
                    return true
                }
                FileOperationTargetConflictResolution.RetryWithReplace -> {
                    replaceExisting = true
                }
                is FileOperationTargetConflictResolution.RetryWithRename -> {
                    moveRcloneUploadPlan(resolvedTarget, resolution.target)
                    resolvedTarget = resolution.target
                }
                FileOperationTargetConflictResolution.Skip -> {
                    transferInfo.skipFile(source)
                    trackedItem?.skip()
                    postCopyMoveNotification(transferInfo, source, type)
                    return false
                }
                FileOperationTargetConflictResolution.Interrupt -> throw InterruptedIOException()
            }
        } catch (e: InvalidFileNameException) {
            throw e
        } catch (e: InterruptedIOException) {
            throw e
        } catch (e: IOException) {
            logFileOperationIOException(e)
            when (
                FileOperationStatePolicy.decide(
                    FileOperationFailureContext(
                        type = when (type) {
                            CopyMoveType.COPY -> FileOperationType.COPY
                            CopyMoveType.EXTRACT -> FileOperationType.EXTRACT
                            CopyMoveType.MOVE -> FileOperationType.MOVE
                        },
                        sourcePath = source.toString(),
                        targetPath = resolvedTarget.toString(),
                        phase = FileOperationPhase.EXECUTE,
                        exception = e,
                        retryCount = retryCount,
                        maxRetries = FileOperationStatePolicy.MAX_RETRY_ATTEMPTS,
                        hasPartialOutput = resolvedTarget.existsInCommittedStorage(
                            LinkOption.NOFOLLOW_LINKS
                        ),
                        userCancelled = false,
                        metadataOptional =
                            copyAttributes &&
                                type == CopyMoveType.MOVE &&
                                resolvedTarget.existsInCommittedStorage(
                                    LinkOption.NOFOLLOW_LINKS
                                ),
                        allowSkip = true
                    )
                )
            ) {
                FileOperationFailureDecision.Retry -> {
                    ++retryCount
                    continue
                }
                FileOperationFailureDecision.Abort,
                FileOperationFailureDecision.RollbackAndAbort -> throw InterruptedIOException().apply {
                    initCause(e)
                }
                FileOperationFailureDecision.CleanupAndContinue -> {
                    transferInfo.recordMetadataWarning(resolvedTarget, e)
                    transferInfo.incrementTransferredFileCount()
                    trackedItem?.complete(resolvedTarget, transferInfo.transferredSize)
                    postCopyMoveNotification(transferInfo, source, type)
                    return true
                }
                else -> Unit
            }
            if (actionAllInfo.skipCopyMoveError) {
                transferInfo.skipFile(source)
                trackedItem?.skip()
                postCopyMoveNotification(transferInfo, source, type)
                return false
            }
            if (e is UserActionRequiredException) {
                val result = showUserAction(e)
                if (result) {
                    continue
                }
            }
            if (!claimPermissionErrorDecision(resolvedTarget, e)) {
                throw InterruptedIOException().apply { initCause(e) }
            }
            val activeTransferId = transferId
            val decisionId = activeTransferId?.let {
                TransferRepository.beginDecision(
                    it,
                    trackedItem?.item?.id ?: 0,
                    "TRANSFER_ERROR",
                    "${e.javaClass.simpleName}: ${e.message.orEmpty()}",
                    "retry,skip,cancel"
                )
            }
            val result = showErrorDialog(
                getString(
                    type.getResourceId(
                        R.string.file_job_copy_error_title_format,
                        R.string.file_job_extract_error_title_format,
                        R.string.file_job_move_error_title_format
                    ), getFileName(source)
                ),
                getString(
                    type.getResourceId(
                        R.string.file_job_copy_error_message_format,
                        R.string.file_job_extract_error_message_format,
                        R.string.file_job_move_error_message_format
                    ), getFileName(targetParent), e.toString()
                ),
                getReadOnlyFileStore(resolvedTarget, e),
                true,
                getString(R.string.retry),
                getString(R.string.skip),
                getString(android.R.string.cancel)
            )
            if (decisionId != null) {
                TransferRepository.resolveDecision(
                    activeTransferId,
                    decisionId,
                    result.action.name,
                    result.isAll
                )
            }
            when (result.action) {
                FileOperationErrorAction.POSITIVE -> {
                    ++retryCount
                    if (retryCount > FileOperationStatePolicy.MAX_RETRY_ATTEMPTS) {
                        throw InterruptedIOException().apply { initCause(e) }
                    }
                    continue
                }
                FileOperationErrorAction.NEGATIVE -> {
                    if (result.isAll) {
                        actionAllInfo.skipCopyMoveError = true
                    }
                    transferInfo.skipFile(source)
                    postCopyMoveNotification(transferInfo, source, type)
                    return false
                }
                FileOperationErrorAction.CANCELED,
                FileOperationErrorAction.NEUTRAL -> {
                    requestCancellation()
                    throw InterruptedIOException()
                }
            }
        }
    }
}

internal fun FileOperationJob.postCopyMoveNotification(
    transferInfo: TransferInfo,
    currentSource: Path,
    type: CopyMoveType
) {
    postTransferSizeNotification(
        transferInfo,
        currentSource,
        type.getResourceId(
            R.string.file_job_copy_notification_title_one_format,
            R.string.file_job_extract_notification_title_one_format,
            R.string.file_job_move_notification_title_one_format
        ),
        type.getResourceId(
            R.plurals.file_job_copy_notification_title_multiple_format,
            R.plurals.file_job_extract_notification_title_multiple_format,
            R.plurals.file_job_move_notification_title_multiple_format
        )
    )
}
