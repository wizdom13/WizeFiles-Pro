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

class ArchiveFileOperationJob(
    private val sources: List<Path>,
    private val archiveFile: Path,
    private val format: Int,
    private val filter: Int,
    private val password: String?
) : FileOperationJob() {
    @Throws(IOException::class)
    override fun run() {
        val scanInfo = scan(sources, R.plurals.file_job_archive_scan_notification_title_format)
        val channel = archiveFile.newByteChannel(
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
        )
        var successful = false
        try {
            channel.use {
                ArchiveWriter(channel, format, filter, password).use { writer ->
                    val transferInfo = TransferInfo(scanInfo, archiveFile)
                    for (source in sources) {
                        val target = getTargetFileName(source)
                        archiveRecursively(source, writer, target, transferInfo)
                        throwIfInterrupted()
                    }
                }
            }
            successful = true
        } finally {
            if (!successful) {
                try {
                    archiveFile.deleteIfExists()
                } catch (e: IOException) {
                    logFileOperationIOException(e)
                } catch (e: UnsupportedOperationException) {
                    logFileOperationIOException(e)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun archiveRecursively(
        source: Path,
        writer: ArchiveWriter,
        target: Path,
        transferInfo: TransferInfo
    ) {
        ArchiveTreeTraverser.walkPreOrder(
            source,
            onDirectory = { directory ->
                val directoryInTarget = target.resolveForeign(source.relativize(directory))
                archive(directory, writer, directoryInTarget, archiveFile, transferInfo)
                throwIfInterrupted()
            },
            onFile = { file ->
                val fileInTarget = target.resolveForeign(source.relativize(file))
                archive(file, writer, fileInTarget, archiveFile, transferInfo)
                throwIfInterrupted()
            }
        )
    }
}

@Throws(IOException::class)
private fun FileOperationJob.archive(
    file: Path,
    writer: ArchiveWriter,
    entryName: Path,
    archiveFile: Path,
    transferInfo: TransferInfo
) {
    try {
        postArchiveNotification(transferInfo, file)
        writer.write(file, entryName, PROGRESS_INTERVAL_MILLIS) {
            transferInfo.addToTransferredSize(it)
            postArchiveNotification(transferInfo, file)
        }
        transferInfo.incrementTransferredFileCount()
        postArchiveNotification(transferInfo, file)
    } catch (e: InterruptedIOException) {
        throw e
    } catch (e: IOException) {
        logFileOperationIOException(e)
        val result = showErrorDialog(
            getString(R.string.file_job_archive_error_title_format, getFileName(file)),
            getString(
                R.string.file_job_archive_error_message_format, getFileName(archiveFile),
                e.toString()
            ),
            getReadOnlyFileStore(archiveFile, e),
            false,
            null,
            getString(android.R.string.cancel),
            null
        )
        when (result.action) {
            FileOperationErrorAction.NEGATIVE,
            FileOperationErrorAction.CANCELED -> {
                requestCancellation()
                throw InterruptedIOException()
            }
            else -> throw AssertionError(result.action)
        }
    }
}

