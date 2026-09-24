package com.wisso.wizefiles.feature.filejobs

import androidx.annotation.PluralsRes
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import com.wisso.wizefiles.R
import com.wisso.wizefiles.storage.StorageFacade
import com.wisso.wizefiles.storage.local.LocalFileNode
import com.wisso.wizefiles.storage.local.LocalTreeTraverser
import com.wisso.wizefiles.storage.provider.ProviderTreeTraverser
import com.wisso.wizefiles.provider.common.readAttributes
import com.wisso.wizefiles.provider.document.isDocumentPath
import com.wisso.wizefiles.provider.ftp.isFtpPath
import com.wisso.wizefiles.provider.os.isLinuxPath
import com.wisso.wizefiles.provider.smb.isSmbPath
import com.wisso.wizefiles.provider.sftp.isSftpPath
import java.io.IOException
import java.io.InterruptedIOException

private val storageFacade = StorageFacade()

internal fun FileOperationJob.scan(
    sources: List<Path?>,
    @PluralsRes notificationTitleRes: Int,
    operationType: FileOperationType = FileOperationType.COPY
): ScanInfo {
    val scanInfo = ScanInfo()
    for (sourceOrNull in sources) {
        val source = sourceOrNull ?: continue
        if (source.isLinuxPath) {
            scanLocal(source, scanInfo, notificationTitleRes)
        } else if (source.isDocumentPath || source.isSmbPath || source.isFtpPath || source.isSftpPath) {
            scanProvider(source, scanInfo, notificationTitleRes, operationType)
        } else {
            Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
                @Throws(IOException::class)
                override fun preVisitDirectory(
                    directory: Path,
                    attributes: BasicFileAttributes
                ): FileVisitResult {
                    scanPath(attributes, scanInfo, notificationTitleRes)
                    throwIfInterrupted()
                    return FileVisitResult.CONTINUE
                }

                @Throws(IOException::class)
                override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                    scanPath(attributes, scanInfo, notificationTitleRes)
                    throwIfInterrupted()
                    return FileVisitResult.CONTINUE
                }

                @Throws(IOException::class)
                override fun visitFileFailed(file: Path, exception: IOException): FileVisitResult {
                    var retryCount = 0
                    var currentException = exception!!
                    while (true) {
                        val decision = resolveTreeFailure(
                            operationType = FileOperationType.COPY,
                            phase = FileOperationPhase.SCAN,
                            path = file,
                            exception = currentException,
                            allowSkip = true,
                            retryCount = retryCount,
                            dialogSpec = FileOperationErrorDialogSpec(
                                title = getString(R.string.file_job_copy_error_title_format, getFileName(file)),
                                message = getString(
                                    R.string.file_job_copy_error_message_format,
                                    getFileName(file.parent ?: file),
                                    currentException.toString()
                                ),
                                readOnlyFileStore = getReadOnlyFileStore(file, currentException),
                                showAll = true,
                                positiveButtonText = getString(R.string.retry),
                                negativeButtonText = getString(R.string.skip),
                                neutralButtonText = getString(android.R.string.cancel)
                            ),
                            onSkip = {}
                        )
                        if (decision != FileOperationRetryDecision.RETRY) {
                            return FileVisitResult.CONTINUE
                        }
                        if (retryCount >= FileOperationStatePolicy.MAX_RETRY_ATTEMPTS) {
                            throw InterruptedIOException().apply { initCause(currentException) }
                        }
                        ++retryCount
                        try {
                            val attributes = file.readAttributes(
                                BasicFileAttributes::class.java,
                                LinkOption.NOFOLLOW_LINKS
                            )
                            scanPath(attributes, scanInfo, notificationTitleRes)
                            throwIfInterrupted()
                            return FileVisitResult.CONTINUE
                        } catch (e: IOException) {
                            currentException = e
                        }
                    }
                }
            })
        }
    }
    postScanNotification(scanInfo, notificationTitleRes)
    return scanInfo
}

@Throws(IOException::class)
internal fun FileOperationJob.scanLocal(
    source: Path,
    scanInfo: ScanInfo,
    @PluralsRes notificationTitleRes: Int
) {
    val localNodes = LocalTreeTraverser.walkPostOrder(LocalFileNode(source.toFile()))
    for (node in localNodes) {
        val metadata = storageFacade.statLocal(node) ?: continue
        scanLocalMetadata(metadata, scanInfo, notificationTitleRes)
        throwIfInterrupted()
    }
}

@Throws(IOException::class)
internal fun FileOperationJob.scanProvider(
    source: Path,
    scanInfo: ScanInfo,
    @PluralsRes notificationTitleRes: Int,
    operationType: FileOperationType = FileOperationType.COPY
) {
    val snapshot = scanInfo.snapshot()
    var retryCount = 0
    while (true) {
        try {
            ProviderTreeTraverser.walkPreOrder(
                source,
                onDirectory = { _, metadata ->
                    scanLocalMetadata(metadata, scanInfo, notificationTitleRes)
                    throwIfInterrupted()
                },
                onFile = { _, metadata ->
                    scanLocalMetadata(metadata, scanInfo, notificationTitleRes)
                    throwIfInterrupted()
                }
            )
            return
        } catch (e: InterruptedIOException) {
            scanInfo.restore(snapshot)
            throw e
        } catch (e: IOException) {
            scanInfo.restore(snapshot)
            logFileOperationIOException(e)
            when (
                resolveRetryOrCancel(
                    source,
                    e,
                    providerScanErrorDialogSpec(source, e, operationType)
                )
            ) {
                FileOperationRetryDecision.RETRY -> {
                    if (retryCount >= FileOperationStatePolicy.MAX_RETRY_ATTEMPTS) {
                        throw InterruptedIOException().apply { initCause(e) }
                    }
                    ++retryCount
                }
                FileOperationRetryDecision.INTERRUPT -> {
                    throw InterruptedIOException().apply { initCause(e) }
                }
                FileOperationRetryDecision.COMPLETE -> return
            }
        }
    }
}

private fun FileOperationJob.providerScanErrorDialogSpec(
    source: Path,
    exception: IOException,
    operationType: FileOperationType
): FileOperationErrorDialogSpec {
    if (operationType == FileOperationType.DELETE) {
        return FileOperationErrorDialogSpec(
            title = getString(R.string.file_job_delete_error_title),
            message = getString(
                R.string.file_job_delete_error_message_format,
                getFileName(source),
                exception.toString()
            ),
            readOnlyFileStore = getReadOnlyFileStore(source, exception),
            showAll = false,
            positiveButtonText = getString(R.string.retry),
            negativeButtonText = getString(android.R.string.cancel),
            neutralButtonText = null
        )
    }
    val titleRes = when (operationType) {
        FileOperationType.EXTRACT -> R.string.file_job_extract_error_title_format
        FileOperationType.MOVE -> R.string.file_job_move_error_title_format
        else -> R.string.file_job_copy_error_title_format
    }
    val messageRes = when (operationType) {
        FileOperationType.EXTRACT -> R.string.file_job_extract_error_message_format
        FileOperationType.MOVE -> R.string.file_job_move_error_message_format
        else -> R.string.file_job_copy_error_message_format
    }
    return FileOperationErrorDialogSpec(
        title = getString(titleRes, getFileName(source)),
        message = getString(
            messageRes,
            getFileName(source.parent ?: source),
            exception.toString()
        ),
        readOnlyFileStore = getReadOnlyFileStore(source, exception),
        showAll = false,
        positiveButtonText = getString(R.string.retry),
        negativeButtonText = getString(android.R.string.cancel),
        neutralButtonText = null
    )
}

@Throws(IOException::class)
internal fun FileOperationJob.scan(source: Path, @PluralsRes notificationTitleRes: Int): ScanInfo {
    return scan(listOf(source), notificationTitleRes)
}

@Throws(IOException::class)
internal fun FileOperationJob.scan(
    source: Path,
    recursive: Boolean,
    @PluralsRes notificationTitleRes: Int
): ScanInfo {
    if (recursive) {
        return scan(source, notificationTitleRes)
    }
    val scanInfo = ScanInfo()
    val attributes = source.readAttributes(
        BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS
    )
    scanPath(attributes, scanInfo, notificationTitleRes)
    throwIfInterrupted()
    return scanInfo
}

internal fun FileOperationJob.scanPath(
    attributes: BasicFileAttributes,
    scanInfo: ScanInfo,
    @PluralsRes notificationTitleRes: Int
) {
    scanInfo.incrementFileCount()
    scanInfo.addToSize(attributes.size())
    postScanNotification(scanInfo, notificationTitleRes)
}

internal fun FileOperationJob.scanLocalMetadata(
    metadata: com.wisso.wizefiles.storage.FileMetadata,
    scanInfo: ScanInfo,
    @PluralsRes notificationTitleRes: Int
) {
    scanInfo.incrementFileCount()
    scanInfo.addToSize(metadata.sizeBytes ?: 0L)
    postScanNotification(scanInfo, notificationTitleRes)
}

internal class ScanInfo {
    var fileCount = 0
        private set
    var size = 0L
        private set

    private var lastNotificationTimeMillis = 0L

    fun incrementFileCount() {
        ++fileCount
    }

    fun addToSize(size: Long) {
        this.size += size
    }

    fun snapshot(): Snapshot = Snapshot(fileCount, size)

    fun restore(snapshot: Snapshot) {
        fileCount = snapshot.fileCount
        size = snapshot.size
    }

    data class Snapshot(val fileCount: Int, val size: Long)

    fun shouldPostNotification(): Boolean {
        val currentTimeMillis = System.currentTimeMillis()
        return if (fileCount % 100 == 0
            || lastNotificationTimeMillis + NOTIFICATION_INTERVAL_MILLIS < currentTimeMillis) {
            lastNotificationTimeMillis = currentTimeMillis
            true
        } else {
            false
        }
    }
}
