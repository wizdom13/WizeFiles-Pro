package com.wisso.wizefiles.feature.filejobs

import com.wisso.wizefiles.feature.transfer.LongRunningOperationLimiter
import com.wisso.wizefiles.feature.transfer.TransferOperationState
import com.wisso.wizefiles.feature.transfer.TransferRepository
import com.wisso.wizefiles.util.WakeWifiLock
import com.wisso.wizefiles.util.removeFirst
import java.util.concurrent.Executors
import java.util.concurrent.Future

internal class FileOperationRuntime(
    private val service: FileOperationService,
    maximumConcurrentTransfers: Int
) {
    private val wakeWifiLock = WakeWifiLock(FileOperationService::class.java.simpleName)
    private val quickJobExecutor = Executors.newCachedThreadPool()
    private val transferExecutor = Executors.newFixedThreadPool(maximumConcurrentTransfers)
    private val runningJobs = mutableMapOf<FileOperationJob, Future<*>>()

    val jobCount: Int
        get() = synchronized(runningJobs) { runningJobs.size }

    fun start(job: FileOperationJob) {
        // Synchronize on runningJobs to prevent a job from removing itself before being added.
        synchronized(runningJobs) {
            val executor = if (job.transferId != null) transferExecutor else quickJobExecutor
            val future = executor.submit {
                try {
                    if (job.transferId != null) {
                        var acquired = false
                        try {
                            LongRunningOperationLimiter.acquire()
                            acquired = true
                            job.runOn(service)
                        } finally {
                            if (acquired) LongRunningOperationLimiter.release()
                        }
                    } else {
                        job.runOn(service)
                    }
                } finally {
                    synchronized(runningJobs) {
                        runningJobs.remove(job)
                        updateWakeWifiLockLocked()
                    }
                }
            }
            runningJobs[job] = future
            updateWakeWifiLockLocked()
        }
    }

    fun cancelJob(id: Int) {
        synchronized(runningJobs) {
            val running = runningJobs.removeFirst { it.key.id == id }
            running?.key?.requestCancellation()
            running?.key?.transferId?.let {
                runCatching { TransferRepository.transition(it, TransferOperationState.CANCELLED) }
            }
            running?.value?.cancel(true)
            updateWakeWifiLockLocked()
        }
    }

    fun cancelTransfer(operationId: String): Boolean {
        synchronized(runningJobs) {
            val running = runningJobs.entries.firstOrNull { it.key.transferId == operationId }
                ?: return false
            running.key.requestCancellation()
            runCatching {
                TransferRepository.transition(operationId, TransferOperationState.CANCELLED)
            }
            runningJobs.remove(running.key)
            running.value.cancel(true)
            updateWakeWifiLockLocked()
            return true
        }
    }

    fun destroy() {
        synchronized(runningJobs) {
            while (runningJobs.isNotEmpty()) {
                runningJobs.removeFirst().value.cancel(true)
            }
            updateWakeWifiLockLocked()
        }
    }

    fun markTimedOut() {
        synchronized(runningJobs) {
            runningJobs.keys.mapNotNull(FileOperationJob::transferId).forEach { operationId ->
                runCatching {
                    TransferRepository.transition(
                        operationId,
                        TransferOperationState.RECOVERABLE,
                        reason = "FOREGROUND_SERVICE_TIMEOUT"
                    )
                }
            }
        }
    }

    // Called while holding runningJobs to avoid acquiring the lock after short jobs finish.
    private fun updateWakeWifiLockLocked() {
        wakeWifiLock.isAcquired = runningJobs.isNotEmpty()
    }
}
