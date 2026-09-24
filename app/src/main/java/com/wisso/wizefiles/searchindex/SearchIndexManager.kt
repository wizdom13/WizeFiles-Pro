package com.wisso.wizefiles.searchindex

import android.os.Environment
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.LiveData
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.wisso.wizefiles.core.app.application
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.provider.archive.isArchivePath
import com.wisso.wizefiles.provider.rclone.isRclonePath
import com.wisso.wizefiles.provider.sftp.isSftpPath
import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.storage.StorageVolumeListLiveData
import com.wisso.wizefiles.util.valueCompat
import com.wisso.wizefiles.util.backgroundExecutor
import java.nio.file.Path
import java.util.concurrent.TimeUnit

object SearchIndexManager {
    private const val UNIQUE_UPDATE_WORK = "search-index-update"
    private const val UNIQUE_PERIODIC_WORK = "search-index-periodic-repair"
    private var observersRegistered = false

    fun initialize() {
        if (Settings.INDEXED_SEARCH.valueCompat) {
            registerExternalChangeObservers()
            ensureScheduled()
            schedulePeriodicRepair()
        }
    }

    fun ensureScheduled() {
        if (!Settings.INDEXED_SEARCH.valueCompat || !hasStorageAccess()) return
        backgroundExecutor.execute {
            val workManager = WorkManager.getInstance(application)
            val hasActiveWork = runCatching {
                workManager.getWorkInfosForUniqueWork(UNIQUE_UPDATE_WORK).get().any {
                    it.state == WorkInfo.State.ENQUEUED ||
                        it.state == WorkInfo.State.RUNNING ||
                        it.state == WorkInfo.State.BLOCKED
                }
            }.getOrDefault(false)
            val status = SearchIndexDatabase.status().state
            if (status == SearchIndexStatus.State.EMPTY ||
                (!hasActiveWork && status != SearchIndexStatus.State.READY)) {
                updateIndex()
            }
        }
    }

    fun updateIndex(rebuild: Boolean = false, userInitiated: Boolean = false) {
        if (!Settings.INDEXED_SEARCH.valueCompat || !hasStorageAccess()) return
        val request = OneTimeWorkRequestBuilder<SearchIndexWorker>()
            .setInputData(
                workDataOf(
                    SearchIndexWorker.KEY_REBUILD to rebuild,
                    SearchIndexWorker.KEY_USER_INITIATED to userInitiated
                )
            )
            .addTag(SearchIndexWorker.TAG)
            .build()
        WorkManager.getInstance(application).enqueueUniqueWork(
            UNIQUE_UPDATE_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun scheduleRepairAfterFileOperation() {
        if (!Settings.INDEXED_SEARCH.valueCompat || !hasStorageAccess()) return
        val request = OneTimeWorkRequestBuilder<SearchIndexWorker>()
            .setInitialDelay(10, TimeUnit.MINUTES)
            .addTag(SearchIndexWorker.TAG)
            .build()
        WorkManager.getInstance(application).enqueueUniqueWork(
            UNIQUE_UPDATE_WORK,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            registerExternalChangeObservers()
            updateIndex(userInitiated = true)
            schedulePeriodicRepair()
        } else {
            cancelUpdates()
        }
    }

    fun clearIndex() {
        cancelUpdates()
        SearchIndexDatabase.clear()
    }

    fun status(): SearchIndexStatus = SearchIndexDatabase.status()

    fun databaseSizeBytes(): Long = SearchIndexDatabase.sizeBytes()

    fun workInfoLiveData(): LiveData<List<WorkInfo>> =
        WorkManager.getInstance(application).getWorkInfosForUniqueWorkLiveData(UNIQUE_UPDATE_WORK)

    fun search(
        path: Path,
        query: String,
        includeHidden: Boolean,
        limit: Int,
        offset: Int
    ): List<FileItem>? {
        if (!Settings.INDEXED_SEARCH.valueCompat || !isLocalIndexablePath(path)) return null
        val normalizedPath = path.toAbsolutePath().normalize().toString()
        val rootPath = SearchIndexDatabase.readyRootFor(normalizedPath) ?: return null
        return SearchIndexDatabase.search(
            rootPath = rootPath,
            directoryPath = normalizedPath,
            query = query,
            includeHidden = includeHidden,
            limit = limit,
            offset = offset
        ).map(SearchIndexRecord::toFileItem)
    }

    fun reconcileDirectory(path: Path, files: List<FileItem>) {
        if (!Settings.INDEXED_SEARCH.valueCompat || !isLocalIndexablePath(path)) return
        val directoryPath = path.toAbsolutePath().normalize().toString()
        val (rootPath, generation) = SearchIndexDatabase.completedRootFor(directoryPath) ?: return
        val records = files.filterNot { it.attributesNoFollowLinks.isSymbolicLink }.map { file ->
            val attributes = file.attributesNoFollowLinks
            SearchIndexRecord(
                rootPath = rootPath,
                path = file.path.rawPath,
                parentPath = directoryPath,
                name = file.path.name,
                isDirectory = attributes.isDirectory,
                sizeBytes = attributes.sizeBytes ?: -1L,
                modifiedMillis = attributes.lastModifiedEpochMillis ?: -1L,
                isHidden = file.isHidden,
                mimeType = file.mimeType.value,
                generation = generation
            )
        }
        SearchIndexDatabase.upsertBatch(records)
        SearchIndexDatabase.deleteMissingDirectChildren(
            rootPath,
            directoryPath,
            records.mapTo(hashSetOf(), SearchIndexRecord::path)
        )
    }

    internal fun isLocalIndexablePath(path: Path): Boolean =
        !path.isArchivePath && !path.isRclonePath && !path.isSftpPath &&
            runCatching {
                val scheme = path.fileSystem.provider().scheme
                scheme.equals("file", ignoreCase = true) || scheme.equals("linux", ignoreCase = true)
            }.getOrDefault(false)

    private fun schedulePeriodicRepair() {
        val request = PeriodicWorkRequestBuilder<SearchIndexWorker>(1, TimeUnit.DAYS)
            .addTag(SearchIndexWorker.TAG)
            .build()
        WorkManager.getInstance(application).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun cancelUpdates() {
        WorkManager.getInstance(application).cancelUniqueWork(UNIQUE_UPDATE_WORK)
        WorkManager.getInstance(application).cancelUniqueWork(UNIQUE_PERIODIC_WORK)
    }

    private fun hasStorageAccess(): Boolean = Environment.isExternalStorageManager()

    @Synchronized
    private fun registerExternalChangeObservers() {
        if (observersRegistered) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { registerExternalChangeObservers() }
            return
        }
        observersRegistered = true
        application.contentResolver.registerContentObserver(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            true,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    scheduleRepairAfterFileOperation()
                }
            }
        )
        StorageVolumeListLiveData.observeForever { scheduleRepairAfterFileOperation() }
    }
}
