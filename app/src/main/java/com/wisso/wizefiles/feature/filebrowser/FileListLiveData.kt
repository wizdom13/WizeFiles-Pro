package com.wisso.wizefiles.feature.filebrowser

import java.nio.file.DirectoryIteratorException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.core.files.model.PendingFileOperationState
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.mime.guessFromPath
import com.wisso.wizefiles.core.files.model.toFileItem
import com.wisso.wizefiles.feature.filejobs.FileOperationService
import com.wisso.wizefiles.provider.archive.isArchivePath
import com.wisso.wizefiles.provider.os.LinuxFileSystemProvider
import com.wisso.wizefiles.provider.rclone.RcloneFileSystemProvider
import com.wisso.wizefiles.provider.rclone.RcloneUploadDisplayState
import com.wisso.wizefiles.provider.rclone.addRcloneUploadCacheListener
import com.wisso.wizefiles.provider.rclone.isRclonePath
import com.wisso.wizefiles.provider.rclone.removeRcloneUploadCacheListener
import com.wisso.wizefiles.provider.rclone.rcloneUploadDisplayState
import com.wisso.wizefiles.provider.root.LibSuFileServiceLauncher
import com.wisso.wizefiles.provider.sftp.isSftpPath
import com.wisso.wizefiles.storage.FileMetadata
import com.wisso.wizefiles.storage.StorageRouter
import com.wisso.wizefiles.storage.local.LocalFileNode
import com.wisso.wizefiles.storage.legacy.LegacyRetrofileAdapter
import com.wisso.wizefiles.storage.legacy.ROOT_ACCESS_REQUIRED_MESSAGE
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.searchindex.SearchIndexManager
import com.wisso.wizefiles.util.CloseableLiveData
import com.wisso.wizefiles.util.Failure
import com.wisso.wizefiles.util.Loading
import com.wisso.wizefiles.util.Stateful
import com.wisso.wizefiles.util.Success
import com.wisso.wizefiles.util.valueCompat
import com.wisso.wizefiles.util.backgroundExecutor
import java.io.IOException
import java.text.Collator
import java.util.concurrent.Future

class FileListLiveData(private val path: Path) : CloseableLiveData<Stateful<List<FileItem>>>() {
    private var future: Future<Unit>? = null

    private val observer: PathObserver
    private val storageRouter = StorageRouter()
    private val legacyRetrofileAdapter = LegacyRetrofileAdapter()
    private val refreshListener: () -> Unit = { onChangeObserved() }
    private val rcloneUploadCacheListener: () -> Unit = { onChangeObserved() }

    @Volatile
    private var isChangedWhileInactive = false

    init {
        FileOperationService.addFileListRefreshListener(refreshListener)
        if (path.isRclonePath) {
            addRcloneUploadCacheListener(rcloneUploadCacheListener)
        }
        loadValue()
        observer = PathObserver(path) { onChangeObserved() }
    }

    fun loadValue() {
        future?.cancel(true)
        value = Loading(value?.value)
        future = backgroundExecutor.submit<Unit> {
            val value = try {
                if (path.isDeviceRootPath()) {
                    loadDeviceRootFileList()
                } else {
                    val localNodes = storageRouter.list(path)
                    if (localNodes != null) {
                        loadLocalFileList(path, localNodes)
                    } else if (path.isSftpPath) {
                        loadArchiveFileList(path)
                    } else if (path.isRclonePath) {
                        loadRcloneFileList(path)
                    } else if (path.isArchivePath) {
                        loadArchiveFileList(path)
                    } else {
                        Success(legacyRetrofileAdapter.listFileItems(path.toAppPath()))
                    }
                }
            } catch (e: Exception) {
                if (Thread.currentThread().isInterrupted) {
                    return@submit
                }
                Failure(valueCompat.value, e)
            }
            if (!Thread.currentThread().isInterrupted) {
                postValue(value)
            }
        }
    }

    private fun Path.isDeviceRootPath(): Boolean = isDeviceRootPathForListing(path = this)

    @Throws(IOException::class)
    private fun loadDeviceRootFileList(): Success<List<FileItem>> {
        val rootPath = resolveDeviceRootPathForListing(
            rootPath = LinuxFileSystemProvider.fileSystem.rootDirectory,
            isSuAvailable = LibSuFileServiceLauncher.isSuAvailable()
        )
        return Success(
            legacyRetrofileAdapter.listFileItems(
                rootPath.toAppPath()
            )
        )
    }

    private fun onChangeObserved() {
        if (hasActiveObservers()) {
            loadValue()
        } else {
            isChangedWhileInactive = true
        }
    }

    override fun onActive() {
        if (isChangedWhileInactive) {
            loadValue()
            isChangedWhileInactive = false
        }
    }

    override fun close() {
        FileOperationService.removeFileListRefreshListener(refreshListener)
        if (path.isRclonePath) {
            removeRcloneUploadCacheListener(rcloneUploadCacheListener)
        }
        observer.close()
        future?.cancel(true)
    }

    private fun loadLocalFileList(path: Path, localNodes: List<LocalFileNode>): Success<List<FileItem>> {
        if (storageRouter.exists(path) == false) {
            return Success(emptyList())
        }
        val fileList = mutableListOf<FileItem>()
        for (node in localNodes) {
            val metadata = storageRouter.stat(node) ?: run {
                continue
            }
            try {
                fileList.add(
                    node.toFileItem(metadata).copy(
                        directoryItemCount = if (metadata.isDirectory) {
                            node.file.list()?.size
                        } else {
                            null
                        }
                    )
                )
            } catch (e: DirectoryIteratorException) {
                com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
            } catch (e: IOException) {
                com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
            }
        }
        runCatching { SearchIndexManager.reconcileDirectory(path, fileList) }
            .onFailure { com.wisso.wizefiles.util.AppLog.w("SearchIndex", "Directory reconciliation failed", it) }
        return Success(fileList)
    }

    private fun loadArchiveFileList(path: Path): Success<List<FileItem>> {
        val fileList = mutableListOf<FileItem>()
        Files.newDirectoryStream(path).use { children ->
            for (childPath in children) {
                ensureRequestIsActive()
                val attributes = Files.readAttributes(childPath, BasicFileAttributes::class.java)
                fileList.add(childPath.toFileItem(attributes))
            }
        }
        return Success(fileList)
    }

    private fun loadRcloneFileList(path: Path): Success<List<FileItem>> {
        val fileList = mutableListOf<FileItem>()
        for (
            entry in RcloneFileSystemProvider.listWithAttributes(
                path,
                applyPendingMutations = true
            )
        ) {
            ensureRequestIsActive()
            fileList.add(entry.path.toFileItem(entry.attributes))
        }
        return Success(fileList)
    }

    private fun Path.toFileItem(attributes: BasicFileAttributes): FileItem {
        val metadata = FileMetadata(
            isDirectory = attributes.isDirectory,
            sizeBytes = attributes.takeIf { !it.isDirectory }?.size(),
            lastModifiedEpochMillis = attributes.lastModifiedTime().toMillis(),
            isSymbolicLink = attributes.isSymbolicLink
        )
        val mimeType = if (metadata.isDirectory) {
            MimeType.DIRECTORY
        } else {
            MimeType.guessFromPath(toString())
        }
        return FileItem(
            path = toAppPath(),
            nameCollationKey = Collator.getInstance()
                .getCollationKeyForFileName(fileName.toString()),
            attributesNoFollowLinks = metadata,
            symbolicLinkTarget = null,
            symbolicLinkTargetAttributes = null,
            isHidden = false,
            mimeType = mimeType,
            pendingOperationState = when (rcloneUploadDisplayState(this)) {
                RcloneUploadDisplayState.QUEUED -> PendingFileOperationState.QUEUED
                RcloneUploadDisplayState.COPYING -> PendingFileOperationState.COPYING
                null -> null
            }
        )
    }

    private fun ensureRequestIsActive() {
        if (Thread.currentThread().isInterrupted) {
            throw java.io.InterruptedIOException("file list request cancelled")
        }
    }

}

internal fun isDeviceRootPathForListing(
    path: Path,
    deviceRootPath: Path = LinuxFileSystemProvider.fileSystem.rootDirectory
): Boolean = path.fileSystem == deviceRootPath.fileSystem && path.normalize() == deviceRootPath

@Throws(IOException::class)
internal fun resolveDeviceRootPathForListing(rootPath: Path, isSuAvailable: Boolean): Path {
    if (!isSuAvailable) {
        throw IOException(ROOT_ACCESS_REQUIRED_MESSAGE)
    }
    return rootPath
}
