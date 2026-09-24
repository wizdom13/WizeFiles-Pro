package com.wisso.wizefiles.searchindex

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.storage.StorageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.android.compat.pathFileCompat
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.mime.guessFromPath
import com.wisso.wizefiles.recyclebin.RecycleBinManager
import com.wisso.wizefiles.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.atomic.AtomicLong

class SearchIndexWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {

    @Volatile
    private var foregroundEnabled = false

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (inputData.getBoolean(KEY_REBUILD, false)) SearchIndexDatabase.clear()
        foregroundEnabled = tryEnterForeground(0L)
        val roots = mountedRoots(applicationContext)
        if (roots.isEmpty()) return@withContext Result.retry()
        SearchIndexDatabase.markUnavailableRootsExcept(
            roots.mapTo(hashSetOf()) { it.toAbsolutePath().normalize().toString() }
        )
        var totalItems = 0L
        var completedRoots = 0
        for (root in roots) {
            if (isStopped) throw CancellationException("Search indexing cancelled")
            val generation = nextGeneration()
            val rootPath = root.toAbsolutePath().normalize().toString()
            SearchIndexDatabase.beginRootScan(rootPath, generation)
            try {
                val rootItems = scanRoot(root, rootPath, generation, totalItems)
                SearchIndexDatabase.completeRootScan(rootPath, generation, rootItems)
                totalItems += rootItems
                completedRoots++
            } catch (cancelled: CancellationException) {
                SearchIndexDatabase.failRootScan(rootPath)
                throw cancelled
            } catch (exception: Exception) {
                SearchIndexDatabase.failRootScan(rootPath)
                AppLog.e("SearchIndex", "Failed to index root=$rootPath", exception)
            }
        }
        if (completedRoots == 0) Result.retry() else Result.success(
            workDataOf(KEY_INDEXED_COUNT to totalItems)
        )
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo(0L)

    private suspend fun scanRoot(
        root: Path,
        rootPath: String,
        generation: Long,
        previouslyIndexed: Long
    ): Long {
        val batch = ArrayList<SearchIndexRecord>(BATCH_SIZE)
        val count = AtomicLong()
        val recycleRoot = RecycleBinManager.recycleBinRootPath.toAbsolutePath().normalize()

        fun flush() {
            if (batch.isEmpty()) return
            SearchIndexDatabase.upsertBatch(batch)
            batch.clear()
            val indexed = previouslyIndexed + count.get()
            SearchIndexDatabase.updateRootProgress(rootPath, count.get())
            setProgressAsync(workDataOf(KEY_INDEXED_COUNT to indexed))
            updateForeground(indexed)
        }

        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(
                directory: Path,
                attributes: BasicFileAttributes
            ): FileVisitResult {
                if (isStopped) return FileVisitResult.TERMINATE
                val normalized = directory.toAbsolutePath().normalize()
                if (normalized == recycleRoot || normalized.startsWith(recycleRoot)) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                if (normalized != root.toAbsolutePath().normalize()) {
                    addRecord(normalized, attributes, rootPath, generation, batch, count)
                    if (batch.size >= BATCH_SIZE) flush()
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                if (isStopped) return FileVisitResult.TERMINATE
                addRecord(file.toAbsolutePath().normalize(), attributes, rootPath, generation, batch, count)
                if (batch.size >= BATCH_SIZE) flush()
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exception: IOException): FileVisitResult =
                FileVisitResult.CONTINUE
        })
        if (isStopped) throw CancellationException("Search indexing cancelled")
        flush()
        return count.get()
    }

    private fun addRecord(
        path: Path,
        attributes: BasicFileAttributes,
        rootPath: String,
        generation: Long,
        batch: MutableList<SearchIndexRecord>,
        count: AtomicLong
    ) {
        if (attributes.isSymbolicLink) return
        val name = path.fileName?.toString() ?: return
        val isDirectory = attributes.isDirectory
        batch += SearchIndexRecord(
            rootPath = rootPath,
            path = path.toString(),
            parentPath = path.parent?.toString().orEmpty(),
            name = name,
            isDirectory = isDirectory,
            sizeBytes = if (isDirectory) -1L else attributes.size(),
            modifiedMillis = attributes.lastModifiedTime()?.toMillis() ?: -1L,
            isHidden = name.startsWith('.'),
            mimeType = if (isDirectory) MimeType.DIRECTORY.value else MimeType.guessFromPath(name).value,
            generation = generation
        )
        count.incrementAndGet()
    }

    private suspend fun tryEnterForeground(indexedCount: Long): Boolean {
        if (!inputData.getBoolean(KEY_USER_INITIATED, false)) {
            return false
        }
        return try {
            setForeground(createForegroundInfo(indexedCount))
            true
        } catch (exception: Exception) {
            if (!exception.isRecoverableSearchIndexForegroundFailure()) throw exception
            AppLog.w(
                "SearchIndex",
                "Foreground start was denied; continuing as scheduled background work",
                exception
            )
            false
        }
    }

    private fun updateForeground(indexedCount: Long) {
        if (!foregroundEnabled) return
        val update = setForegroundAsync(createForegroundInfo(indexedCount))
        update.addListener(
            {
                runCatching { update.get() }
                    .onFailure { exception ->
                        foregroundEnabled = false
                        AppLog.w(
                            "SearchIndex",
                            "Foreground progress update failed; continuing in background",
                            exception
                        )
                    }
            },
            applicationContext.mainExecutor
        )
    }

    private fun createForegroundInfo(indexedCount: Long): ForegroundInfo {
        val notificationManager = applicationContext.getSystemService<NotificationManager>()!!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.search_index_notification_title),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_search_index_notification_24dp)
            .setContentTitle(applicationContext.getString(R.string.search_index_notification_title))
            .setContentText(
                applicationContext.getString(
                    R.string.search_index_notification_progress,
                    indexedCount
                )
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val TAG = "search-index"
        const val KEY_REBUILD = "rebuild"
        const val KEY_USER_INITIATED = "user_initiated"
        const val KEY_INDEXED_COUNT = "indexed_count"
        private const val CHANNEL_ID = "search_index"
        private const val NOTIFICATION_ID = 0x57495A45
        private const val BATCH_SIZE = 750

        private val generationCounter = AtomicLong(System.currentTimeMillis())
        private fun nextGeneration(): Long = generationCounter.incrementAndGet()

        internal fun mountedRoots(context: Context): List<Path> =
            context.getSystemService(StorageManager::class.java).storageVolumes
                .mapNotNull { it.pathFileCompat?.toPath() }
                .filter { Files.isDirectory(it) && Files.isReadable(it) }
                .map { it.toAbsolutePath().normalize() }
                .distinct()
    }
}


internal fun Throwable.isRecoverableSearchIndexForegroundFailure(): Boolean {
    val causes = generateSequence(this) { it.cause }.toList()
    if (causes.any { it is CancellationException }) return false
    return causes.any { cause ->
        cause is SecurityException ||
            cause is IllegalStateException ||
            cause.javaClass.name == "android.app.ForegroundServiceStartNotAllowedException" ||
            cause.javaClass.name == "android.app.BackgroundServiceStartNotAllowedException"
    }
}
