package com.wisso.wizefiles.feature.filejobs

import java.nio.channels.Channels
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.app.mainExecutor
import com.wisso.wizefiles.storage.StorageFacade
import com.wisso.wizefiles.provider.common.PosixFileModeBit
import com.wisso.wizefiles.provider.common.PosixGroup
import com.wisso.wizefiles.provider.common.PosixUser
import com.wisso.wizefiles.provider.common.copyTo
import com.wisso.wizefiles.provider.common.deleteIfExists
import com.wisso.wizefiles.provider.common.exists
import com.wisso.wizefiles.provider.common.getMode
import com.wisso.wizefiles.provider.common.isDirectory
import com.wisso.wizefiles.provider.common.moveTo
import com.wisso.wizefiles.provider.common.newByteChannel
import com.wisso.wizefiles.provider.common.newOutputStream
import com.wisso.wizefiles.provider.common.restoreSeLinuxContext
import com.wisso.wizefiles.provider.common.setGroup
import com.wisso.wizefiles.provider.common.setMode
import com.wisso.wizefiles.provider.common.setOwner
import com.wisso.wizefiles.provider.common.setSeLinuxContext
import com.wisso.wizefiles.util.showToast
import com.wisso.wizefiles.util.toEnumSet
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.UUID

private val storageFacade = StorageFacade()

class RestoreFileSeLinuxContextJob(
    private val path: Path,
    private val recursive: Boolean
) : FileOperationJob() {
    @Throws(IOException::class)
    override fun run() {
        val scanInfo = scan(
            path, recursive,
            R.plurals.file_job_restore_selinux_context_scan_notification_title_format
        )
        val transferInfo = TransferInfo(scanInfo, null)
        val actionAllInfo = FileOperationActionAllInfo()
        FileOperationMetadataService(this, transferInfo, actionAllInfo)
            .restoreSeLinuxContext(path, recursive)
        reportMetadataWarnings(transferInfo)
    }
}

class SaveFileOperationJob(private val source: Path, private val target: Path) : FileOperationJob() {
    override fun run() {
        save(source, target)
    }
}

@Throws(IOException::class)
private fun FileOperationJob.save(source: Path, target: Path) {
    val scanInfo = scan(source, R.plurals.file_job_copy_scan_notification_title_format)
    val targetParent = target.parent
    val transferInfo = TransferInfo(scanInfo, targetParent)
    val actionAllInfo = FileOperationActionAllInfo(replace = true)
    val copied = copy(source, target, false, transferInfo, actionAllInfo)
    if (!copied) {
        return
    }
    showToast(
        getString(R.string.save_as_success_format, getFileName(target), getFileName(targetParent))
    )
}

class SetFileGroupJob(
    private val path: Path,
    private val group: PosixGroup,
    private val recursive: Boolean
) : FileOperationJob() {
    @Throws(IOException::class)
    override fun run() {
        val scanInfo = scan(
            path, recursive, R.plurals.file_job_set_group_scan_notification_title_format
        )
        val transferInfo = TransferInfo(scanInfo, null)
        val actionAllInfo = FileOperationActionAllInfo()
        FileOperationMetadataService(this, transferInfo, actionAllInfo)
            .setGroup(path, group, recursive)
        reportMetadataWarnings(transferInfo)
    }
}

class SetFileModeJob(
    private val path: Path,
    private val mode: Set<PosixFileModeBit>,
    private val recursive: Boolean,
    private val uppercaseX: Boolean
) : FileOperationJob() {
    @Throws(IOException::class)
    override fun run() {
        val scanInfo = scan(
            path, recursive, R.plurals.file_job_set_mode_scan_notification_title_format
        )
        val transferInfo = TransferInfo(scanInfo, null)
        val actionAllInfo = FileOperationActionAllInfo()
        FileOperationMetadataService(this, transferInfo, actionAllInfo).setMode(path, recursive) { file, attributes ->
            if (attributes.isSymbolicLink) {
                null
            } else if (!attributes.isDirectory) {
                getFileMode(file)
            } else {
                mode
            }
        }
        reportMetadataWarnings(transferInfo)
    }

    @Throws(IOException::class)
    private fun getFileMode(file: Path): Set<PosixFileModeBit> {
        if (file == path || !uppercaseX) {
            return mode
        }
        val mode = mode.toEnumSet()
        val currentMode = file.getMode(LinkOption.NOFOLLOW_LINKS)!!
        if (PosixFileModeBit.OWNER_EXECUTE !in currentMode) {
            mode -= PosixFileModeBit.OWNER_EXECUTE
        }
        if (PosixFileModeBit.GROUP_EXECUTE !in currentMode) {
            mode -= PosixFileModeBit.GROUP_EXECUTE
        }
        if (PosixFileModeBit.OTHERS_EXECUTE !in currentMode) {
            mode -= PosixFileModeBit.OTHERS_EXECUTE
        }
        return mode
    }
}

class SetFileOwnerJob(
    private val path: Path,
    private val owner: PosixUser,
    private val recursive: Boolean
) : FileOperationJob() {
    @Throws(IOException::class)
    override fun run() {
        val scanInfo = scan(
            path, recursive, R.plurals.file_job_set_owner_scan_notification_title_format
        )
        val transferInfo = TransferInfo(scanInfo, null)
        val actionAllInfo = FileOperationActionAllInfo()
        FileOperationMetadataService(this, transferInfo, actionAllInfo)
            .setOwner(path, owner, recursive)
        reportMetadataWarnings(transferInfo)
    }
}

class SetFileSeLinuxContextJob(
    private val path: Path,
    private val seLinuxContext: String,
    private val recursive: Boolean
) : FileOperationJob() {
    @Throws(IOException::class)
    override fun run() {
        val scanInfo = scan(
            path, recursive, R.plurals.file_job_set_selinux_context_scan_notification_title_format
        )
        val transferInfo = TransferInfo(scanInfo, null)
        val actionAllInfo = FileOperationActionAllInfo()
        FileOperationMetadataService(this, transferInfo, actionAllInfo)
            .setSeLinuxContext(path, seLinuxContext, recursive)
        reportMetadataWarnings(transferInfo)
    }
}

class WriteFileOperationJob(
    private val file: Path,
    private val content: ByteArray,
    private val listener: ((Boolean) -> Unit)?
) : FileOperationJob() {
    @Throws(IOException::class)
    override fun run() {
        var successful = false
        try {
            successful = write(file, content)
        } finally {
            listener?.let { mainExecutor.execute { it(successful) } }
        }
    }
}

@Throws(IOException::class)
private fun FileOperationJob.write(file: Path, content: ByteArray): Boolean {
    val scanInfo = ScanInfo().apply {
        incrementFileCount()
        addToSize(content.size.toLong())
    }
    return runWithRetryPolicyResult(
        block = {
            val transferInfo = TransferInfo(scanInfo, file)
            file.newOutputStream().use { outputStream ->
                ByteArrayInputStream(content).copyTo(outputStream, PROGRESS_INTERVAL_MILLIS) {
                    transferInfo.addToTransferredSize(it)
                    postWriteNotification(transferInfo)
                }
                postWriteNotification(transferInfo)
            }
            true
        },
        onIOException = { e, _ ->
            resolveRetryOrReturn(
                file,
                e,
                FileOperationErrorDialogSpec(
                    title = getString(R.string.file_job_write_error_title, getFileName(file)),
                    message = getString(
                        R.string.file_job_write_error_message_format,
                        getFileName(file),
                        e.toString()
                    ),
                    readOnlyFileStore = getReadOnlyFileStore(file, e),
                    showAll = false,
                    positiveButtonText = getString(R.string.retry),
                    negativeButtonText = getString(android.R.string.cancel),
                    neutralButtonText = null
                ),
                returnValue = false
            )
        }
    )
}

class EncryptFileOperationJob(
    private val paths: List<Path>,
    private val password: CharArray,
    private val algorithmId: Int,
    private val kdfId: Int
) : FileOperationJob() {
    @Throws(IOException::class)
    override fun run() {
        val algorithm = FileEncryptionAlgorithm.fromId(algorithmId)
        val kdf = FileKdfAlgorithm.fromId(kdfId)
        for (path in paths) {
            encryptRecursively(path, algorithm, kdf)
        }
        notifyFileListRefresh()
    }

    private fun encryptRecursively(path: Path, algorithm: FileEncryptionAlgorithm, kdf: FileKdfAlgorithm) {
        if (path.isDirectory()) {
            Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (!file.fileName.toString().endsWith(".enc")) {
                        encryptOne(file, algorithm, kdf)
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        } else {
            encryptOne(path, algorithm, kdf)
        }
    }

    private fun encryptOne(path: Path, algorithm: FileEncryptionAlgorithm, kdf: FileKdfAlgorithm) {
        val target = path.resolveSibling("${path.fileName}.enc")
        if (target.exists()) {
            throw FileAlreadyExistsException(target.toString())
        }
        var targetCreated = false
        try {
            path.newByteChannel(StandardOpenOption.READ).use { input ->
                target.newByteChannel(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
                    targetCreated = true
                    FileCrypto.encrypt(
                        Channels.newInputStream(input),
                        Channels.newOutputStream(output),
                        password.copyOf(),
                        algorithm,
                        kdf,
                        path.fileName.toString()
                    )
                }
            }
        } catch (throwable: Throwable) {
            if (targetCreated) {
                runCatching { target.deleteIfExists() }
            }
            throw throwable
        }
    }
}

class DecryptFileOperationJob(private val paths: List<Path>, private val password: CharArray) : FileOperationJob() {
    @Throws(IOException::class)
    override fun run() {
        for (path in paths) {
            decryptRecursively(path)
        }
        notifyFileListRefresh()
    }

    private fun decryptRecursively(path: Path) {
        if (path.isDirectory()) {
            Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (file.fileName.toString().endsWith(".enc")) {
                        decryptOne(file)
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        } else {
            if (!path.fileName.toString().endsWith(".enc")) {
                throw FileCryptoException("Only .enc files can be decrypted")
            }
            decryptOne(path)
        }
    }

    private fun decryptOne(path: Path) {
        val fallbackName = FileCrypto.requireSafeOriginalName(
            path.fileName.toString().removeSuffix(".enc")
        )
        val fallbackDestination = path.resolveSibling(fallbackName)
        if (fallbackDestination.exists()) {
            throw FileAlreadyExistsException(fallbackDestination.toString())
        }
        val temporary = path.resolveSibling(
            ".${fallbackName.take(80)}.wizefiles-decrypt-${UUID.randomUUID()}.part"
        )
        var published = false
        try {
            val header = path.newByteChannel(StandardOpenOption.READ).use { input ->
                temporary.newByteChannel(
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
                ).use { output ->
                    FileCrypto.decrypt(
                        Channels.newInputStream(input),
                        Channels.newOutputStream(output),
                        password.copyOf()
                    )
                }
            }
            val preferredName = FileCrypto.requireSafeOriginalName(header.originalName)
            val preferredDestination = path.resolveSibling(preferredName).normalize()
            val expectedParent = path.parent?.normalize()
            if (expectedParent != null && preferredDestination.parent?.normalize() != expectedParent) {
                throw FileCryptoException("Encrypted file has an unsafe original name")
            }
            val destination = if (
                preferredDestination != fallbackDestination &&
                !preferredDestination.exists()
            ) {
                preferredDestination
            } else {
                fallbackDestination
            }
            if (destination.exists()) {
                throw FileAlreadyExistsException(destination.toString())
            }
            temporary.moveTo(destination)
            published = true
        } catch (e: FileCryptoException) {
            throw IOException(e.message, e)
        } finally {
            if (!published) {
                runCatching { temporary.deleteIfExists() }
            }
        }
    }
}

