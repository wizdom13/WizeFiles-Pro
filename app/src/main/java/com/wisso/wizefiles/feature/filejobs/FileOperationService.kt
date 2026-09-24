// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filejobs

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import java.nio.file.Path
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.provider.common.PosixFileModeBit
import com.wisso.wizefiles.provider.common.PosixGroup
import com.wisso.wizefiles.provider.common.PosixUser
import com.wisso.wizefiles.util.ForegroundNotificationManager
import com.wisso.wizefiles.util.removeFirst
import com.wisso.wizefiles.core.android.compat.removeFirstCompat
import com.wisso.wizefiles.searchindex.SearchIndexManager
import com.wisso.wizefiles.feature.transfer.TransferExecutionPolicy
import com.wisso.wizefiles.feature.transfer.TransferOperationType
import com.wisso.wizefiles.feature.transfer.TransferRepository
import com.wisso.wizefiles.provider.archive.archiveFile
import com.wisso.wizefiles.provider.archive.editor.ArchiveConflictPolicy
import com.wisso.wizefiles.feature.transfer.OperationControlRegistry
import com.wisso.wizefiles.feature.transfer.TransferOperationState
import com.wisso.wizefiles.feature.transfer.TransferRecoveryManager
import com.wisso.wizefiles.feature.sync.SyncRepository
import com.wisso.wizefiles.feature.sync.SyncResumeWorker
import com.wisso.wizefiles.feature.sync.SyncRunState
import com.wisso.wizefiles.feature.apksigning.ApkSigningSecretRegistry
import com.wisso.wizefiles.feature.apksigning.ApkSigningSecrets
import com.wisso.wizefiles.feature.apksigning.ApkSigningWorkflowSpec
import com.wisso.wizefiles.feature.apksigning.AabSigningWorkflowSpec
import com.wisso.wizefiles.feature.apksigning.ApksSigningWorkflowSpec
import com.wisso.wizefiles.feature.apksigning.XapkSigningWorkflowSpec
import com.wisso.wizefiles.feature.apksigning.ApkmImportWorkflowSpec

class FileOperationService : Service() {
    internal lateinit var notificationManager: ForegroundNotificationManager
        private set

    private lateinit var runtime: FileOperationRuntime

    override fun onCreate() {
        super.onCreate()

        runtime = FileOperationRuntime(this, TransferExecutionPolicy.MAXIMUM_CONCURRENT_TRANSFERS)
        notificationManager = ForegroundNotificationManager(this)
        instance = this
        TransferRepository.recoverInterrupted()

        val pendingTransferIds = pendingJobs.mapNotNull(FileOperationJob::transferId).toSet()
        TransferRecoveryManager.recoverProcessLoss(pendingTransferIds, ::startJob)
        while (pendingJobs.isNotEmpty()) {
            startJob(pendingJobs.removeFirstCompat())
        }
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private val jobCount: Int
        get() = runtime.jobCount

    internal fun startJob(job: FileOperationJob) {
        runtime.start(job)
    }

    private fun cancelJob(id: Int) {
        runtime.cancelJob(id)
    }

    private fun cancelTransfer(operationId: String): Boolean =
        runtime.cancelTransfer(operationId)

    override fun onDestroy() {
        super.onDestroy()

        instance = null
        runtime.destroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        runtime.markTimedOut()
        stopSelf(startId)
    }

    companion object {
        private val mainHandler = Handler(Looper.getMainLooper())
        private var instance: FileOperationService? = null

        private val pendingJobs = mutableListOf<FileOperationJob>()
        private val fileListRefreshListeners = mutableSetOf<() -> Unit>()

        val runningJobCount: Int
            @MainThread
            get() = instance?.jobCount ?: 0

        @MainThread
        internal fun enqueue(job: FileOperationJob, context: Context) {
            val instance = instance
            if (instance != null) {
                instance.startJob(job)
            } else {
                pendingJobs.add(job)
                context.startService(Intent(context, FileOperationService::class.java))
            }
        }


        fun archive(
            sources: List<Path>,
            archiveFile: Path,
            format: Int,
            filter: Int,
            password: String?,
            context: Context
        ) = FileOperationCommandCoordinator.archive(
            sources, archiveFile, format, filter, password, context
        )

        fun copy(
            sources: List<Path>,
            targetDirectory: Path,
            context: Context,
            archiveConflictPolicy: ArchiveConflictPolicy = ArchiveConflictPolicy.FAIL
        ) = FileOperationCommandCoordinator.copy(
            sources, targetDirectory, context, archiveConflictPolicy
        )

        fun create(path: Path, createDirectory: Boolean, context: Context) =
            FileOperationCommandCoordinator.create(path, createDirectory, context)

        fun delete(
            paths: List<Path>,
            context: Context,
            options: DeleteOptions = DeleteOptions()
        ): String? = FileOperationCommandCoordinator.delete(paths, context, options)

        fun move(
            sources: List<Path>,
            targetDirectory: Path,
            context: Context,
            archiveConflictPolicy: ArchiveConflictPolicy = ArchiveConflictPolicy.FAIL
        ) = FileOperationCommandCoordinator.move(
            sources, targetDirectory, context, archiveConflictPolicy
        )

        fun installApk(file: Path, context: Context) =
            FileOperationCommandCoordinator.installApk(file, context)

        fun signApk(
            spec: ApkSigningWorkflowSpec,
            secrets: ApkSigningSecrets,
            context: Context
        ): String = PackageSigningCoordinator.signApk(spec, secrets, context)

        @MainThread
        fun resumeApkSigning(
            operationId: String,
            secrets: ApkSigningSecrets,
            context: Context
        ): Boolean = PackageSigningCoordinator.resumeApkSigning(operationId, secrets, context)

        fun signAab(
            spec: AabSigningWorkflowSpec,
            secrets: ApkSigningSecrets,
            context: Context
        ): String = PackageSigningCoordinator.signAab(spec, secrets, context)

        @MainThread
        fun resumeAabSigning(
            operationId: String,
            secrets: ApkSigningSecrets,
            context: Context
        ): Boolean = PackageSigningCoordinator.resumeAabSigning(operationId, secrets, context)

        fun signApks(
            spec: ApksSigningWorkflowSpec,
            secrets: ApkSigningSecrets,
            context: Context
        ): String = PackageSigningCoordinator.signApks(spec, secrets, context)

        @MainThread
        fun resumeApksSigning(
            operationId: String,
            secrets: ApkSigningSecrets,
            context: Context
        ): Boolean = PackageSigningCoordinator.resumeApksSigning(operationId, secrets, context)

        fun signXapk(
            spec: XapkSigningWorkflowSpec,
            secrets: ApkSigningSecrets,
            context: Context
        ): String = PackageSigningCoordinator.signXapk(spec, secrets, context)

        @MainThread
        fun resumeXapkSigning(
            operationId: String,
            secrets: ApkSigningSecrets,
            context: Context
        ): Boolean = PackageSigningCoordinator.resumeXapkSigning(operationId, secrets, context)

        fun importApkm(spec: ApkmImportWorkflowSpec, context: Context): String =
            PackageSigningCoordinator.importApkm(spec, context)

        fun open(file: Path, mimeType: MimeType, withChooser: Boolean, context: Context) =
            FileOperationCommandCoordinator.open(file, mimeType, withChooser, context)

        fun openInternalViewer(file: Path, mimeType: MimeType, context: Context) =
            FileOperationCommandCoordinator.openInternalViewer(file, mimeType, context)

        fun rename(path: Path, newName: String, context: Context) =
            FileOperationCommandCoordinator.rename(path, newName, context)

        fun batchRename(operations: List<BatchRenameOperation>, context: Context) =
            FileOperationCommandCoordinator.batchRename(operations, context)

        fun encrypt(
            paths: List<Path>,
            password: CharArray,
            algorithmId: Int,
            kdfId: Int,
            context: Context
        ) = FileOperationCommandCoordinator.encrypt(paths, password, algorithmId, kdfId, context)

        fun decrypt(paths: List<Path>, password: CharArray, context: Context) =
            FileOperationCommandCoordinator.decrypt(paths, password, context)

        fun restoreSeLinuxContext(path: Path, recursive: Boolean, context: Context) =
            FileOperationCommandCoordinator.restoreSeLinuxContext(path, recursive, context)

        fun save(source: Path, target: Path, context: Context) =
            FileOperationCommandCoordinator.save(source, target, context)

        fun setGroup(path: Path, group: PosixGroup, recursive: Boolean, context: Context) =
            FileOperationCommandCoordinator.setGroup(path, group, recursive, context)

        fun setMode(
            path: Path,
            mode: Set<PosixFileModeBit>,
            recursive: Boolean,
            uppercaseX: Boolean,
            context: Context
        ) = FileOperationCommandCoordinator.setMode(path, mode, recursive, uppercaseX, context)

        fun setOwner(path: Path, owner: PosixUser, recursive: Boolean, context: Context) =
            FileOperationCommandCoordinator.setOwner(path, owner, recursive, context)

        fun setSeLinuxContext(
            path: Path,
            seLinuxContext: String,
            recursive: Boolean,
            context: Context
        ) = FileOperationCommandCoordinator.setSeLinuxContext(
            path, seLinuxContext, recursive, context
        )

        fun write(
            file: Path,
            content: ByteArray,
            context: Context,
            listener: ((Boolean) -> Unit)?
        ) = FileOperationCommandCoordinator.write(file, content, context, listener)

        fun cancelJob(id: Int) {
            pendingJobs.removeFirst { it.id == id }
            instance?.cancelJob(id)
        }

        @MainThread
        fun pauseTransfer(operationId: String): Boolean {
            val operation = TransferRepository.operation(operationId) ?: return false
            if (operation.state != TransferOperationState.RUNNING &&
                operation.state != TransferOperationState.PLANNING) {
                return false
            }
            TransferRepository.transition(operationId, TransferOperationState.PAUSE_REQUESTED)
            val signalled = OperationControlRegistry.requestPause(operationId)
            if (!signalled) {
                TransferRepository.transition(operationId, TransferOperationState.PAUSED)
            }
            return true
        }

        @MainThread
        fun resumeTransfer(operationId: String, context: Context): Boolean {
            val operation = TransferRepository.operation(operationId) ?: return false
            if (operation.state != TransferOperationState.PAUSED &&
                operation.state != TransferOperationState.RECOVERABLE &&
                operation.state != TransferOperationState.FAILED &&
                operation.state != TransferOperationState.WAITING_FOR_USER &&
                !(operation.state == TransferOperationState.COMPLETED_WITH_WARNINGS &&
                    operation.failedItems > 0)) {
                return false
            }
            if (operation.type in setOf(
                    TransferOperationType.APK_SIGN,
                    TransferOperationType.AAB_SIGN,
                    TransferOperationType.APKS_SIGN,
                    TransferOperationType.XAPK_SIGN
                ) && !ApkSigningSecretRegistry.has(operationId)) {
                return false
            }
            SyncRepository.runForTransfer(operationId)?.let { syncRun ->
                if (syncRun.state != SyncRunState.PAUSED && syncRun.state != SyncRunState.FAILED &&
                    syncRun.state != SyncRunState.COMPLETED_WITH_WARNINGS) {
                    return false
                }
                if (syncRun.state == SyncRunState.COMPLETED_WITH_WARNINGS ||
                    syncRun.state == SyncRunState.FAILED) {
                    SyncRepository.retryFailedActions(syncRun.id)
                    TransferRepository.retryFailedItems(operationId)
                }
                SyncResumeWorker.enqueue(context, syncRun.id)
                return true
            }
            if (operation.state == TransferOperationState.FAILED ||
                operation.state == TransferOperationState.COMPLETED_WITH_WARNINGS) {
                TransferRepository.retryFailedItems(operationId)
            }
            if (operation.state == TransferOperationState.WAITING_FOR_USER) {
                com.wisso.wizefiles.feature.transfer.TransferDatabase
                    .abandonPendingDecisions(operationId)
            }
            TransferRepository.transition(operationId, TransferOperationState.QUEUED)
            val queued = requireNotNull(TransferRepository.operation(operationId))
            val job = TransferRecoveryManager.createJob(queued) ?: return false
            enqueue(job, context)
            return true
        }

        @MainThread
        fun cancelTransfer(operationId: String): Boolean {
            SyncRepository.runForTransfer(operationId)?.let { syncRun ->
                val signalled = OperationControlRegistry.requestCancel(operationId)
                if (!signalled) {
                    runCatching { TransferRepository.transition(operationId, TransferOperationState.CANCELLED) }
                    runCatching { SyncRepository.transitionRun(syncRun.id, SyncRunState.CANCELLED) }
                }
                return true
            }
            val operation = TransferRepository.operation(operationId) ?: return false
            if (TransferRepository.removeQueued(operationId)) {
                PackageSigningCoordinator.cleanup(operation)
                return true
            }
            if (instance?.cancelTransfer(operationId) == true) {
                PackageSigningCoordinator.cleanup(operation)
                return true
            }
            if (operation.state in setOf(
                    TransferOperationState.PAUSED,
                    TransferOperationState.RECOVERABLE,
                    TransferOperationState.WAITING_FOR_USER,
                    TransferOperationState.FAILED
                )) {
                TransferRepository.transition(operationId, TransferOperationState.CANCELLED)
                PackageSigningCoordinator.cleanup(operation)
                return true
            }
            return false
        }


        fun addFileListRefreshListener(listener: () -> Unit) {
            synchronized(fileListRefreshListeners) {
                fileListRefreshListeners += listener
            }
        }

        @MainThread
        fun removeFileListRefreshListener(listener: () -> Unit) {
            synchronized(fileListRefreshListeners) {
                fileListRefreshListeners -= listener
            }
        }

        internal fun notifyFileListRefresh() {
            runCatching { SearchIndexManager.scheduleRepairAfterFileOperation() }
            mainHandler.post {
                val listeners = synchronized(fileListRefreshListeners) {
                    fileListRefreshListeners.toList()
                }
                listeners.forEach { it() }
            }
        }
    }
}
