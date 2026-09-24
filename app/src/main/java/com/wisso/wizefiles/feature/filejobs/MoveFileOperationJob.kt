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

class MoveFileOperationJob(
    private val sources: List<Path>,
    private val targetDirectory: Path,
    transferId: String? = null
) : FileOperationJob(transferId) {
    @Throws(IOException::class)
    override fun run() {
        val scanInfo = scan(sources, R.plurals.file_job_move_scan_notification_title_format)
        beginTransferExecution(scanInfo.fileCount.toLong(), scanInfo.size)
        val transferInfo = TransferInfo(scanInfo, targetDirectory)
        val sourcesToMove = mutableListOf<Path>()
        for (source in sources) {
            val target = targetDirectory.resolveForeign(source.fileName)
            try {
                moveAtomically(source, target)
                transferInfo.addTransferredFile(target)
            } catch (e: InterruptedIOException) {
                throw e
            } catch (e: IOException) {
                sourcesToMove.add(source)
            }
            throwIfInterrupted()
        }
        val actionAllInfo = FileOperationActionAllInfo()
        val transferEngine = MoveFileOperationTransferEngine(this, transferInfo, actionAllInfo)
        for (source in sourcesToMove) {
            val target = targetDirectory.resolveForeign(source.fileName)
            transferEngine.moveRecursively(source, target)
            throwIfInterrupted()
        }
        reportMetadataWarnings(transferInfo)
        notifyFileListRefresh()
    }

}

internal fun resolveLocalRelativePath(targetRoot: Path, relativePath: String): Path {
    return try {
        targetRoot.resolveForeign(Paths.get(relativePath))
    } catch (e: RuntimeException) {
        targetRoot.resolve(relativePath)
    }
}
