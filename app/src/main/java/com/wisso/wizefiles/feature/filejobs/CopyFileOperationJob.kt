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

class CopyFileOperationJob(
    private val sources: List<Path>,
    private val targetDirectory: Path,
    transferId: String? = null
) : FileOperationJob(transferId) {
    @Throws(IOException::class)
    override fun run() {
        val isExtract = sources.all { it.isArchivePath }
        val copyTargets = sources.map { source ->
            source to if (source.parent == targetDirectory) {
                getTargetPathForDuplicate(source)
            } else {
                targetDirectory.resolveForeign(getTargetFileName(source))
            }
        }
        preflightRemoteWrite(targetDirectory)
        val uploadPlans = copyTargets.mapNotNull { (source, target) ->
            runCatching {
                RcloneUploadPlanEntry(
                    target,
                    Files.readAttributes(
                        source,
                        BasicFileAttributes::class.java,
                        LinkOption.NOFOLLOW_LINKS
                    )
                )
            }.getOrNull()
        }
        planRcloneUploads(uploadPlans)
        try {
            val scanInfo = scan(
                sources, if (isExtract) {
                    R.plurals.file_job_extract_scan_notification_title_format
                } else {
                    R.plurals.file_job_copy_scan_notification_title_format
                }
            )
            beginTransferExecution(scanInfo.fileCount.toLong(), scanInfo.size)
            val transferInfo = TransferInfo(scanInfo, targetDirectory)
            val actionAllInfo = FileOperationActionAllInfo()
            val transferEngine = CopyFileOperationTransferEngine(
                this, isExtract, transferInfo, actionAllInfo
            )
            for ((source, target) in copyTargets) {
                markRcloneUploadCopying(target)
                val copied = transferEngine.copyRecursively(source, target)
                if (copied) {
                    completeRcloneUploadPlan(target)
                } else {
                    removeRcloneUploadPlans(listOf(target))
                }
                throwIfInterrupted()
            }
            reportMetadataWarnings(transferInfo)
            notifyFileListRefresh()
        } finally {
            // A successful staging upload removes its own plan. Anything left was skipped,
            // failed, cancelled, or still queued and must disappear from the browser overlay.
            removeRcloneUploadPlans(copyTargets.map { it.second })
        }
    }

    private fun getTargetPathForDuplicate(source: Path): Path {
        val sourceFileName = (source as? ByteStringListPath<*>)?.fileNameByteString
        if (sourceFileName != null) {
            // We do want to follow symbolic links here.
            val countEndIndex = if (source.isDirectory()) {
                sourceFileName.length
            } else {
                sourceFileName.asFileName().baseName.length
            }
            val countInfo = getDuplicateCountInfo(sourceFileName, countEndIndex)
            var i = countInfo.count + 1
            while (i > 0) {
                val targetFileName = setDuplicateCount(sourceFileName, countInfo, i)
                val target = source.resolveSibling(targetFileName)
                if (!target.exists(LinkOption.NOFOLLOW_LINKS)) {
                    return target
                }
                ++i
            }
            // Just leave it to conflict handling logic.
            return source
        }
        return getTargetPathForDuplicateLocal(source)
    }

    private fun getTargetPathForDuplicateLocal(source: Path): Path {
        val sourceFileName = source.fileName?.toString() ?: return source
        val countEndIndex = if (source.isDirectory()) {
            sourceFileName.length
        } else {
            sourceFileName.substringBeforeLast('.', sourceFileName).length
        }
        val countInfo = getDuplicateCountInfo(sourceFileName, countEndIndex)
        var i = countInfo.count + 1
        while (i > 0) {
            val targetFileName = setDuplicateCount(sourceFileName, countInfo, i)
            val target = source.resolveSibling(targetFileName)
            if (!target.exists(LinkOption.NOFOLLOW_LINKS)) {
                return target
            }
            ++i
        }
        return source
    }

    private fun getDuplicateCountInfo(fileName: ByteString, countEnd: Int): DuplicateCountInfo {
        while (true) {
            // /(?<=.) \(\d+\)$/
            var index = countEnd - 1
            // \)
            if (index < 0 || fileName[index] != ')'.code.toByte()) {
                break
            }
            --index
            // \d+
            val digitsEndInclusive = index
            while (index >= 0) {
                val b = fileName[index]
                if (b < '0'.code.toByte() || b > '9'.code.toByte()) {
                    break
                }
                --index
            }
            if (index == digitsEndInclusive) {
                break
            }
            val countString = fileName.substring(index + 1, digitsEndInclusive + 1).toString()
            val count = try {
                countString.toInt()
            } catch (e: NumberFormatException) {
                break
            }
            // \(
            if (index < 0 || fileName[index] != '('.code.toByte()) {
                break
            }
            --index
            //
            if (index < 0 || fileName[index] != ' '.code.toByte()) {
                break
            }
            // (?<=.)
            if (index == 0) {
                break
            }
            return DuplicateCountInfo(index, countEnd, count)
        }
        return DuplicateCountInfo(countEnd, countEnd, 0)
    }

    private fun setDuplicateCount(
        fileName: ByteString,
        countInfo: DuplicateCountInfo,
        count: Int
    ): ByteString {
        return ByteStringBuilder(fileName.substring(0, countInfo.countStart))
            .append(" ($count)".toByteString())
            .append(fileName.substring(countInfo.countEnd))
            .toByteString()
    }

    private fun getDuplicateCountInfo(fileName: String, countEnd: Int): DuplicateCountInfo {
        val suffixStart = fileName.lastIndexOf(" (", startIndex = countEnd - 1)
        if (suffixStart <= 0 || suffixStart >= countEnd) {
            return DuplicateCountInfo(countEnd, countEnd, 0)
        }
        if (countEnd > fileName.length || countEnd <= suffixStart + 2 || fileName[countEnd - 1] != ')') {
            return DuplicateCountInfo(countEnd, countEnd, 0)
        }
        val digits = fileName.substring(suffixStart + 2, countEnd - 1)
        val count = digits.toIntOrNull() ?: return DuplicateCountInfo(countEnd, countEnd, 0)
        return DuplicateCountInfo(suffixStart, countEnd, count)
    }

    private fun setDuplicateCount(
        fileName: String,
        countInfo: DuplicateCountInfo,
        count: Int
    ): String = buildString {
        append(fileName.substring(0, countInfo.countStart))
        append(" (")
        append(count)
        append(')')
        append(fileName.substring(countInfo.countEnd))
    }

    private class DuplicateCountInfo(val countStart: Int, val countEnd: Int, val count: Int)
}

private fun FileOperationJob.preflightRemoteWrite(targetDirectory: Path) {
    if (!targetDirectory.isRemoteProviderPath) {
        return
    }
    val probe = targetDirectory.resolve(
        ".wizefiles-write-test-${UUID.randomUUID()}"
    )
    runWithRetryPolicy(
        block = {
            var created = false
            try {
                probe.createFile()
                created = true
            } finally {
                if (created) {
                    probe.deleteIfExists()
                }
            }
        },
        onIOException = { exception, _ ->
            resolveRetryOrCancel(
                targetDirectory,
                exception,
                FileOperationErrorDialogSpec(
                    title = getString(R.string.file_job_write_error_title),
                    message = getString(
                        R.string.file_job_write_error_message_format,
                        getFileName(targetDirectory),
                        exception.toString()
                    ),
                    readOnlyFileStore = getReadOnlyFileStore(targetDirectory, exception),
                    showAll = false,
                    positiveButtonText = getString(R.string.retry),
                    negativeButtonText = getString(android.R.string.cancel),
                    neutralButtonText = null
                )
            )
        }
    )
}

private val Path.isRemoteProviderPath: Boolean
    get() = isDocumentPath || isFtpPath || isSftpPath || isSmbPath

