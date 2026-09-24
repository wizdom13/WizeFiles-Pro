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
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.provider.archive.isArchivePath
import com.wisso.wizefiles.provider.common.InvalidFileNameException
import com.wisso.wizefiles.provider.common.ProgressCopyOption
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

internal class CopyFileOperationTransferEngine(
    private val job: FileOperationJob,
    private val isExtract: Boolean,
    private val transferInfo: TransferInfo,
    private val actionAllInfo: FileOperationActionAllInfo
) {
    @Throws(IOException::class)
    fun copyRecursively(source: Path, target: Path): Boolean {
        if (isExtract && source.isArchivePath) {
            return copyRecursivelyArchive(source, target)
        }
        if (source.isLinuxPath && target.isLinuxPath) {
            return copyRecursivelyLocal(source, target)
        }
        var rootCopied = true
        Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
            @Throws(IOException::class)
            override fun preVisitDirectory(
                directory: Path,
                attributes: BasicFileAttributes
            ): FileVisitResult {
                val directoryInTarget = target.resolveForeign(source.relativize(directory))
                val copied = job.copy(
                    directory, directoryInTarget, isExtract, transferInfo, actionAllInfo
                )
                if (directory == source) rootCopied = copied
                job.throwIfInterrupted()
                return if (copied) FileVisitResult.CONTINUE else FileVisitResult.SKIP_SUBTREE
            }

            @Throws(IOException::class)
            override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                val fileInTarget = target.resolveForeign(source.relativize(file))
                val copied = job.copy(file, fileInTarget, isExtract, transferInfo, actionAllInfo)
                if (file == source) rootCopied = copied
                job.throwIfInterrupted()
                return FileVisitResult.CONTINUE
            }

            @Throws(IOException::class)
            override fun visitFileFailed(file: Path, exception: IOException): FileVisitResult {
                var retryCount = 0
                var currentException = exception
                while (true) {
                    val decision = job.resolveTreeFailure(
                        operationType = if (isExtract) FileOperationType.EXTRACT else FileOperationType.COPY,
                        phase = FileOperationPhase.EXECUTE,
                        path = file,
                        exception = currentException,
                        allowSkip = true,
                        retryCount = retryCount,
                        dialogSpec = FileOperationErrorDialogSpec(
                            title = job.getString(
                                if (isExtract) {
                                    R.string.file_job_extract_error_title_format
                                } else {
                                    R.string.file_job_copy_error_title_format
                                },
                                job.getFileName(file)
                            ),
                            message = job.getString(
                                if (isExtract) {
                                    R.string.file_job_extract_error_message_format
                                } else {
                                    R.string.file_job_copy_error_message_format
                                },
                                job.getFileName(target),
                                currentException.toString()
                            ),
                            readOnlyFileStore = job.getReadOnlyFileStore(file, currentException),
                            showAll = true,
                            positiveButtonText = job.getString(R.string.retry),
                            negativeButtonText = job.getString(R.string.skip),
                            neutralButtonText = job.getString(android.R.string.cancel)
                        ),
                        onSkip = {
                            transferInfo.skipFile(file)
                            job.postCopyMoveNotification(
                                transferInfo,
                                file,
                                if (isExtract) CopyMoveType.EXTRACT else CopyMoveType.COPY
                            )
                        }
                    )
                    if (decision != FileOperationRetryDecision.RETRY) {
                        if (file == source) rootCopied = false
                        return FileVisitResult.CONTINUE
                    }
                    if (retryCount >= FileOperationStatePolicy.MAX_RETRY_ATTEMPTS) {
                        throw InterruptedIOException().apply { initCause(currentException) }
                    }
                    ++retryCount
                    try {
                        val fileInTarget = target.resolveForeign(source.relativize(file))
                        job.copy(file, fileInTarget, isExtract, transferInfo, actionAllInfo)
                        job.throwIfInterrupted()
                        return FileVisitResult.CONTINUE
                    } catch (e: IOException) {
                        currentException = e
                    }
                }
            }
        })
        return rootCopied
    }

    @Throws(IOException::class)
    private fun copyRecursivelyArchive(source: Path, target: Path): Boolean {
        var rootCopied = true
        ArchiveTreeTraverser.walkPreOrderWithPrune(
            source,
            onDirectory = { directory ->
                val directoryInTarget = target.resolveForeign(source.relativize(directory))
                val copied = job.copy(directory, directoryInTarget, true, transferInfo, actionAllInfo)
                if (directory == source) rootCopied = copied
                job.throwIfInterrupted()
                copied
            },
            onFile = { file ->
                val fileInTarget = target.resolveForeign(source.relativize(file))
                val copied = job.copy(file, fileInTarget, true, transferInfo, actionAllInfo)
                if (file == source) rootCopied = copied
                job.throwIfInterrupted()
            }
        )
        return rootCopied
    }

    @Throws(IOException::class)
    private fun copyRecursivelyLocal(source: Path, target: Path): Boolean {
        var rootCopied = true
        val sourceRoot = source.toFile()
        LocalTreeTraverser.walkPreOrder(
            LocalFileNode(sourceRoot),
            onDirectory = { directory ->
                val directoryInTarget = getLocalTargetPath(sourceRoot, target, directory.file)
                val copied = job.copy(
                    Paths.get(directory.file.path),
                    directoryInTarget,
                    isExtract,
                    transferInfo,
                    actionAllInfo
                )
                if (directory.file == sourceRoot) rootCopied = copied
                job.throwIfInterrupted()
                copied
            },
            onFile = { file ->
                val fileInTarget = getLocalTargetPath(sourceRoot, target, file.file)
                val copied = job.copy(
                    Paths.get(file.file.path),
                    fileInTarget,
                    isExtract,
                    transferInfo,
                    actionAllInfo
                )
                if (file.file == sourceRoot) rootCopied = copied
                job.throwIfInterrupted()
            }
        )
        return rootCopied
    }

    private fun getLocalTargetPath(sourceRoot: File, targetRoot: Path, sourceFile: File): Path {
        if (sourceFile == sourceRoot) {
            return targetRoot
        }
        val relativePath = sourceRoot.toURI().relativize(sourceFile.toURI()).path.trimEnd('/')
        return resolveLocalRelativePath(targetRoot, relativePath)
    }
}

