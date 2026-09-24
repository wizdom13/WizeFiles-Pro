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

internal class MoveFileOperationTransferEngine(
    private val job: FileOperationJob,
    private val transferInfo: TransferInfo,
    private val actionAllInfo: FileOperationActionAllInfo
) {
    @Throws(IOException::class)
    fun moveRecursively(source: Path, target: Path) {
        if (source.isLinuxPath && target.isLinuxPath) {
            moveRecursivelyLocal(source, target)
            return
        }
        Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
            @Throws(IOException::class)
            override fun preVisitDirectory(
                directory: Path,
                attributes: BasicFileAttributes
            ): FileVisitResult {
                val directoryInTarget = target.resolveForeign(source.relativize(directory))
                try {
                    job.moveAtomically(directory, directoryInTarget)
                    job.throwIfInterrupted()
                    return FileVisitResult.SKIP_SUBTREE
                } catch (e: InterruptedIOException) {
                    throw e
                } catch (e: IOException) {
                    logFileOperationIOException(e)
                }
                val copied = job.copyForMove(directory, directoryInTarget, transferInfo, actionAllInfo)
                job.throwIfInterrupted()
                return if (copied) FileVisitResult.CONTINUE else FileVisitResult.SKIP_SUBTREE
            }

            @Throws(IOException::class)
            override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                val fileInTarget = target.resolveForeign(source.relativize(file))
                try {
                    job.moveAtomically(file, fileInTarget)
                    job.throwIfInterrupted()
                    return FileVisitResult.CONTINUE
                } catch (e: InterruptedIOException) {
                    throw e
                } catch (e: IOException) {
                    logFileOperationIOException(e)
                }
                job.moveByCopy(file, fileInTarget, transferInfo, actionAllInfo)
                job.throwIfInterrupted()
                return FileVisitResult.CONTINUE
            }

            @Throws(IOException::class)
            override fun visitFileFailed(file: Path, exception: IOException): FileVisitResult {
                var retryCount = 0
                var currentException = exception
                while (true) {
                    val decision = job.resolveTreeFailure(
                        operationType = FileOperationType.MOVE,
                        phase = FileOperationPhase.EXECUTE,
                        path = file,
                        exception = currentException,
                        allowSkip = true,
                        retryCount = retryCount,
                        dialogSpec = FileOperationErrorDialogSpec(
                            title = job.getString(R.string.file_job_move_error_title_format, job.getFileName(file)),
                            message = job.getString(
                                R.string.file_job_move_error_message_format,
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
                            job.postCopyMoveNotification(transferInfo, file, CopyMoveType.MOVE)
                        }
                    )
                    if (decision != FileOperationRetryDecision.RETRY) {
                        return FileVisitResult.CONTINUE
                    }
                    if (retryCount >= FileOperationStatePolicy.MAX_RETRY_ATTEMPTS) {
                        throw InterruptedIOException().apply { initCause(currentException) }
                    }
                    ++retryCount
                    try {
                        val fileInTarget = target.resolveForeign(source.relativize(file))
                        job.moveByCopy(file, fileInTarget, transferInfo, actionAllInfo)
                        job.throwIfInterrupted()
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
            ): FileVisitResult? {
                if (exception != null) {
                    throw exception
                }
                job.delete(directory, null, actionAllInfo)
                job.throwIfInterrupted()
                return FileVisitResult.CONTINUE
            }
        })
    }

    @Throws(IOException::class)
    private fun moveRecursivelyLocal(source: Path, target: Path) {
        val sourceRoot = source.toFile()
        LocalTreeTraverser.walkPreOrderWithPost(
            LocalFileNode(sourceRoot),
            onDirectory = { directory ->
                val directoryInTarget = getLocalMoveTargetPath(sourceRoot, target, directory.file)
                try {
                    job.moveAtomically(Paths.get(directory.file.path), directoryInTarget)
                    job.throwIfInterrupted()
                    false
                } catch (e: InterruptedIOException) {
                    throw e
                } catch (e: IOException) {
                    logFileOperationIOException(e)
                    val copied = job.copyForMove(
                        Paths.get(directory.file.path),
                        directoryInTarget,
                        transferInfo,
                        actionAllInfo
                    )
                    job.throwIfInterrupted()
                    copied
                }
            },
            onFile = { file ->
                val fileInTarget = getLocalMoveTargetPath(sourceRoot, target, file.file)
                try {
                    job.moveAtomically(Paths.get(file.file.path), fileInTarget)
                    job.throwIfInterrupted()
                    return@walkPreOrderWithPost
                } catch (e: InterruptedIOException) {
                    throw e
                } catch (e: IOException) {
                    logFileOperationIOException(e)
                }
                job.moveByCopy(Paths.get(file.file.path), fileInTarget, transferInfo, actionAllInfo)
                job.throwIfInterrupted()
            },
            onDirectoryPost = { directory ->
                job.delete(Paths.get(directory.file.path), null, actionAllInfo)
                job.throwIfInterrupted()
            }
        )
    }

    private fun getLocalMoveTargetPath(sourceRoot: File, targetRoot: Path, sourceFile: File): Path {
        if (sourceFile == sourceRoot) {
            return targetRoot
        }
        val relativePath = sourceRoot.toURI().relativize(sourceFile.toURI()).path.trimEnd('/')
        return resolveLocalRelativePath(targetRoot, relativePath)
    }
}

