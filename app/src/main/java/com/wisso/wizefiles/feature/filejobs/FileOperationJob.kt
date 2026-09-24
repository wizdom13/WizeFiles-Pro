package com.wisso.wizefiles.feature.filejobs

import com.wisso.wizefiles.R
import com.wisso.wizefiles.util.showToast
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.file.Path
import java.util.Random
import com.wisso.wizefiles.feature.transfer.TransferOperationState
import com.wisso.wizefiles.feature.transfer.TransferRepository
import com.wisso.wizefiles.feature.transfer.TransferStateMachine
import com.wisso.wizefiles.feature.transfer.OperationControlRegistry
import com.wisso.wizefiles.feature.transfer.PauseRequestedException

abstract class FileOperationJob(val transferId: String? = null) {
    val id = Random().nextInt()

    internal lateinit var service: FileOperationService
        private set
    private var metadataWarningCount: Int = 0
    private val permissionDecisionFingerprints = mutableSetOf<String>()

    @Volatile
    private var cancellationRequested = false

    internal fun requestCancellation() {
        cancellationRequested = true
    }

    internal fun claimPermissionErrorDecision(path: Path, exception: IOException): Boolean {
        if (
            FileOperationStatePolicy.categorize(exception) !=
            FileOperationFailureCategory.PERMISSION
        ) {
            return true
        }
        val rootCause = generateSequence<Throwable>(exception) { it.cause }
            .take(16)
            .last()
        val fingerprint = buildString {
            append(path)
            append('|')
            append(rootCause.javaClass.name)
            append('|')
            append(rootCause.message.orEmpty())
        }
        return synchronized(permissionDecisionFingerprints) {
            permissionDecisionFingerprints.add(fingerprint)
        }
    }

    fun runOn(service: FileOperationService) {
        this.service = service
        var keepCompletionNotification = false
        try {
            transferId?.let(OperationControlRegistry::attach)
            transferId?.let { TransferRepository.transition(it, TransferOperationState.PLANNING) }
            run()
            if (metadataWarningCount > 0) {
                postCompletionWarningNotification(metadataWarningCount)
                keepCompletionNotification = true
                service.showToast(
                    service.resources.getQuantityString(
                        R.plurals.file_job_completion_with_metadata_warning,
                        metadataWarningCount,
                        metadataWarningCount
                    )
                )
            }
            transferId?.let {
                TransferRepository.transition(
                    it,
                    if (metadataWarningCount > 0) {
                        TransferOperationState.COMPLETED_WITH_WARNINGS
                    } else {
                        TransferOperationState.COMPLETED
                    }
                )
            }
        } catch (e: PauseRequestedException) {
            transferId?.let {
                com.wisso.wizefiles.feature.transfer.TransferProgress.tracker.reset(it)
                runCatching { TransferRepository.transition(it, TransferOperationState.PAUSED) }
            }
        } catch (e: InterruptedIOException) {
            val currentState = transferId?.let { TransferRepository.operation(it)?.state }
            if (!cancellationRequested) {
                com.wisso.wizefiles.util.AppLog.w(
                    "Transfer",
                    "File operation interrupted; recovery will be attempted",
                    e
                )
            }
            val targetState = interruptedTransferTargetState(
                cancellationRequested,
                currentState
            )
            if (targetState != null) {
                transferId?.let {
                    runCatching {
                        TransferRepository.transition(
                            it,
                            targetState,
                            reason = if (targetState == TransferOperationState.RECOVERABLE) {
                                "INTERRUPTED"
                            } else {
                                ""
                            }
                        )
                    }
                }
            }
        } catch (e: LinkageError) {
            handleFailure(e)
        } catch (e: Exception) {
            handleFailure(e)
        } finally {
            OperationControlRegistry.detach(transferId)
            if (!keepCompletionNotification) {
                service.notificationManager.cancel(id)
            }
        }
    }

    private fun handleFailure(throwable: Throwable) {
        com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", throwable)
        service.showToast(throwable.toString())
        transferId?.let {
            runCatching {
                TransferRepository.transition(
                    it,
                    TransferOperationState.FAILED,
                    errorCategory = throwable.javaClass.simpleName,
                    errorMessage = throwable.message.orEmpty()
                )
            }
        }
    }

    @Throws(IOException::class)
    protected abstract fun run()

    internal fun reportMetadataWarnings(count: Int) {
        if (count > 0) {
            metadataWarningCount += count
        }
    }

    internal fun beginTransferExecution(totalItems: Long, totalBytes: Long) {
        val id = transferId ?: return
        TransferRepository.updatePlanSummary(id, totalItems, totalBytes)
        TransferRepository.transition(id, TransferOperationState.RUNNING)
    }
}

internal fun interruptedTransferTargetState(
    cancellationRequested: Boolean,
    currentState: TransferOperationState?
): TransferOperationState? {
    if (currentState == null || currentState.isTerminal) {
        return null
    }
    val targetState = if (cancellationRequested) {
        TransferOperationState.CANCELLED
    } else {
        TransferOperationState.RECOVERABLE
    }
    return targetState.takeIf {
        TransferStateMachine.canTransition(currentState, targetState)
    }
}
