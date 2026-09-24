// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.appmanager

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppBackupCleanupWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        AppBackupCache.cleanupExpired(applicationContext.cacheDir)
        Result.success()
    }

    companion object {
        fun schedule(context: Context, retainedForTransfer: Boolean) {
            val delayHours = if (retainedForTransfer) TRANSFER_RETENTION_HOURS else SHARE_RETENTION_HOURS
            val name = if (retainedForTransfer) TRANSFER_CLEANUP_WORK else SHARE_CLEANUP_WORK
            val request = OneTimeWorkRequestBuilder<AppBackupCleanupWorker>()
                .setInitialDelay(delayHours, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                name,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}

internal object AppBackupCache {
    fun createSession(cacheDir: File, retainedForTransfer: Boolean): File {
        val root = File(cacheDir, CACHE_DIRECTORY_NAME)
        val session = File(root, UUID.randomUUID().toString())
        if (!session.mkdirs()) throw java.io.IOException("Unable to create app backup cache")
        if (retainedForTransfer) File(session, TRANSFER_MARKER).createNewFile()
        return session
    }

    fun cleanupExpired(cacheDir: File, nowMillis: Long = System.currentTimeMillis()) {
        val root = File(cacheDir, CACHE_DIRECTORY_NAME)
        root.listFiles()?.filter(File::isDirectory)?.forEach { session ->
            val retainedForTransfer = File(session, TRANSFER_MARKER).isFile
            val retentionMillis = TimeUnit.HOURS.toMillis(
                if (retainedForTransfer) TRANSFER_RETENTION_HOURS else SHARE_RETENTION_HOURS
            )
            if (nowMillis - session.lastModified() >= retentionMillis) {
                session.deleteRecursively()
            }
        }
        if (root.listFiles().isNullOrEmpty()) root.delete()
    }
}

private const val CACHE_DIRECTORY_NAME = "app_backups"
private const val TRANSFER_MARKER = ".retain_for_transfer"
private const val SHARE_RETENTION_HOURS = 24L
private const val TRANSFER_RETENTION_HOURS = 24L * 7L
private const val SHARE_CLEANUP_WORK = "app-backup-share-cleanup"
private const val TRANSFER_CLEANUP_WORK = "app-backup-transfer-cleanup"
