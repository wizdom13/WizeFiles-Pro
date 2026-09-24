package com.wisso.wizefiles.feature.sync

import com.wisso.wizefiles.feature.transfer.TransferDatabase
import com.wisso.wizefiles.feature.transfer.TransferOperationSpec
import com.wisso.wizefiles.feature.transfer.TransferOperationState
import com.wisso.wizefiles.feature.transfer.TransferOperationType
import com.wisso.wizefiles.feature.transfer.TransferProgressCheckpoint
import com.wisso.wizefiles.feature.transfer.TransferRepository
import com.wisso.wizefiles.feature.transfer.LongRunningOperationLimiter
import com.wisso.wizefiles.feature.transfer.OperationControlRegistry
import com.wisso.wizefiles.feature.transfer.PauseRequestedException
import com.wisso.wizefiles.feature.transfer.CancelRequestedException
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.storage.path.toUriString
import com.wisso.wizefiles.storage.StorageFacade
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal data class SyncExecutionResult(
    val completed: Int,
    val failed: Int,
    val blocked: Int,
    val transferOperationId: String,
    val paused: Boolean = false,
    val cancelled: Boolean = false
)

internal class SyncActionExecutor(
    private val scopeLocks: SyncScopeLockRegistry = GlobalSyncScopeLocks.registry,
    private val storageFacade: StorageFacade = StorageFacade()
) {
    fun execute(profile: SyncProfile, run: SyncRun): SyncExecutionResult {
        val actions = SyncRepository.actions(run.id)
        val blocked = actions.count { it.state == SyncActionState.BLOCKED }
        check(blocked == 0) { "Resolve all file conflicts before starting synchronization" }
        check(scopeLocks.tryAcquire(run.id, listOf(profile.sourceUri, profile.destinationUri))) {
            "OVERLAPPING_SYNC_ALREADY_RUNNING"
        }
        var permitAcquired = false
        val executable = actions.filter { it.state == SyncActionState.PENDING }
        val trackable = actions.filter { it.state != SyncActionState.BLOCKED }
        val existingOperation = run.transferOperationId.takeIf(String::isNotBlank)
            ?.let(TransferRepository::operation)
        val operation = try {
            when (existingOperation?.state) {
                TransferOperationState.QUEUED -> requireNotNull(existingOperation)
                TransferOperationState.PAUSED,
                TransferOperationState.RECOVERABLE,
                TransferOperationState.FAILED,
                TransferOperationState.COMPLETED_WITH_WARNINGS ->
                    TransferRepository.transition(
                        requireNotNull(existingOperation).id,
                        TransferOperationState.QUEUED
                    )
                else -> TransferRepository.enqueue(TransferOperationSpec(
                    type = when (profile.mode) {
                        SyncMode.UPDATE_DESTINATION -> TransferOperationType.BACKUP
                        SyncMode.MIRROR -> TransferOperationType.MIRROR
                        SyncMode.TWO_WAY -> TransferOperationType.TWO_WAY_SYNC
                        SyncMode.MOVE_SOURCE -> TransferOperationType.MOVE_BACKUP
                    },
                    sourceUris = executable.mapNotNull { it.sourceUri.takeIf(String::isNotBlank) }
                        .ifEmpty { listOf(profile.sourceUri) },
                    destinationUri = profile.destinationUri
                ))
            }
        } catch (throwable: Throwable) {
            scopeLocks.release(run.id)
            throw throwable
        }
        var completed = 0
        var failed = 0
        try {
            when (run.state) {
                SyncRunState.APPROVED ->
                    SyncRepository.transitionRun(
                        run.id,
                        SyncRunState.QUEUED,
                        transferOperationId = operation.id
                    )
                SyncRunState.FAILED,
                SyncRunState.PAUSED,
                SyncRunState.COMPLETED_WITH_WARNINGS ->
                    SyncRepository.transitionRun(
                        run.id,
                        SyncRunState.QUEUED,
                        transferOperationId = operation.id
                    )
                else -> Unit
            }
            OperationControlRegistry.attach(operation.id)
            LongRunningOperationLimiter.acquire()
            permitAcquired = true
            checkControl(operation.id)
            SyncRepository.transitionRun(run.id, SyncRunState.RUNNING, transferOperationId = operation.id)
            TransferRepository.transition(operation.id, TransferOperationState.PLANNING)
            TransferRepository.updatePlanSummary(
                operation.id,
                trackable.size.toLong(),
                executable.sumOf(::transferSize)
            )
            trackable.filter { it.state == SyncActionState.SKIPPED }.forEach { action ->
                val item = TransferDatabase.beginItem(
                    operation.id,
                    action.sourceUri,
                    action.targetUri,
                    action.relativePath,
                    action.sourceFingerprint.startsWith("d:"),
                    transferSize(action),
                    0,
                    action.sourceFingerprint
                )
                TransferDatabase.skipItem(item.id)
                SyncRepository.updateAction(
                    action.id,
                    SyncActionState.SKIPPED,
                    transferItemId = item.id
                )
            }
            TransferRepository.transition(operation.id, TransferOperationState.RUNNING)
            var transferred = 0L
            executable.sortedWith(
                compareBy<SyncAction> { phase(it) }
                    .thenByDescending {
                        if (it.type == SyncActionType.DELETE) {
                            it.relativePath.count { character -> character == '/' }
                        } else {
                            Int.MIN_VALUE
                        }
                    }
                    .thenBy(SyncAction::ordinal)
            ).forEach { action ->
                checkControl(operation.id)
                try {
                    SyncRepository.updateAction(action.id, SyncActionState.RUNNING)
                    val itemId = executeAction(operation.id, action, transferred)
                    transferred += transferSize(action)
                    SyncRepository.updateAction(
                        action.id,
                        SyncActionState.COMPLETED,
                        transferItemId = itemId,
                        protectedResultUri = if (action.type == SyncActionType.PROTECT) {
                            action.targetUri
                        } else {
                            ""
                        }
                    )
                    completed++
                } catch (pause: PauseRequestedException) {
                    throw pause
                } catch (cancel: CancelRequestedException) {
                    throw cancel
                } catch (throwable: Throwable) {
                    SyncRepository.updateAction(
                        action.id,
                        SyncActionState.FAILED,
                        errorMessage = throwable.message.orEmpty()
                    )
                    failed++
                }
            }
            val transferState = if (failed == 0) {
                TransferOperationState.COMPLETED
            } else {
                TransferOperationState.COMPLETED_WITH_WARNINGS
            }
            TransferRepository.transition(operation.id, transferState)
            SyncRepository.transitionRun(
                run.id,
                if (failed == 0 && blocked == 0) {
                    SyncRunState.COMPLETED
                } else {
                    SyncRunState.COMPLETED_WITH_WARNINGS
                }
            )
            return SyncExecutionResult(completed, failed, blocked, operation.id)
        } catch (_: PauseRequestedException) {
            SyncRepository.resetRunningActions(run.id)
            runCatching { TransferRepository.transition(operation.id, TransferOperationState.PAUSED) }
            runCatching { SyncRepository.transitionRun(run.id, SyncRunState.PAUSED) }
            return SyncExecutionResult(completed, failed, blocked, operation.id, paused = true)
        } catch (_: CancelRequestedException) {
            SyncRepository.resetRunningActions(run.id)
            runCatching { TransferRepository.transition(operation.id, TransferOperationState.CANCELLED) }
            runCatching { SyncRepository.transitionRun(run.id, SyncRunState.CANCELLED) }
            return SyncExecutionResult(completed, failed, blocked, operation.id, cancelled = true)
        } catch (throwable: Throwable) {
            runCatching {
                TransferRepository.transition(
                    operation.id,
                    TransferOperationState.FAILED,
                    errorCategory = throwable.javaClass.simpleName,
                    errorMessage = throwable.message.orEmpty()
                )
            }
            runCatching { SyncRepository.transitionRun(run.id, SyncRunState.FAILED) }
            throw throwable
        } finally {
            OperationControlRegistry.detach(operation.id)
            if (permitAcquired) LongRunningOperationLimiter.release()
            scopeLocks.release(run.id)
        }
    }

    private fun executeAction(operationId: String, action: SyncAction, transferred: Long): Long {
        val source = SyncPathResolver.resolve(action.sourceUri)
        val target = SyncPathResolver.resolve(action.targetUri)
        return when (action.type) {
            SyncActionType.COPY,
            SyncActionType.UPDATE,
            SyncActionType.MOVE,
            SyncActionType.PROTECT -> {
                requireNotNull(source) { "Source path is unavailable" }
                requireNotNull(target) { "Target path is unavailable" }
                val priorItem = TransferDatabase.items(operationId).firstOrNull {
                    it.sourceUri == action.sourceUri && it.targetUri == action.targetUri
                }
                if (
                    priorItem != null && priorItem.temporaryTargetUri.isNotBlank() &&
                    !Files.exists(source, LinkOption.NOFOLLOW_LINKS) &&
                    recoveredTargetIsValid(target, action.sourceFingerprint)
                ) {
                    TransferDatabase.completeItem(priorItem.id, action.targetUri)
                    return priorItem.id
                }
                if (
                    priorItem != null && priorItem.temporaryTargetUri.isNotBlank() &&
                    !Files.exists(source, LinkOption.NOFOLLOW_LINKS)
                ) {
                    error("RECOVERY_SOURCE_AND_TARGET_INVALID")
                }
                revalidate(source, action.sourceFingerprint)
                val attributes = Files.readAttributes(
                    source,
                    java.nio.file.attribute.BasicFileAttributes::class.java,
                    LinkOption.NOFOLLOW_LINKS
                )
                val item = TransferDatabase.beginItem(
                    operationId,
                    action.sourceUri,
                    action.targetUri,
                    action.relativePath,
                    attributes.isDirectory,
                    attributes.size().coerceAtLeast(0),
                    attributes.lastModifiedTime().toMillis().coerceAtLeast(0),
                    action.sourceFingerprint
                )
                if (
                    item.attemptCount > 1 && item.temporaryTargetUri.isNotBlank() &&
                    recoveredTargetIsValid(target, action.sourceFingerprint)
                ) {
                    if (action.type == SyncActionType.MOVE &&
                        Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
                        checkControl(operationId)
                        storageFacade.delete(source)
                    }
                    TransferDatabase.completeItem(item.id, action.targetUri)
                    return item.id
                }
                if (attributes.isDirectory) {
                    storageFacade.createDirectories(target)
                } else {
                    when {
                        action.targetFingerprint.isNotBlank() ->
                            revalidate(target, action.targetFingerprint)
                        action.type != SyncActionType.PROTECT ->
                            check(!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                                "Target appeared after preview"
                            }
                    }
                    target.parent?.let(storageFacade::createDirectories)
                    val temporary = target.resolveSibling(
                        ".wizefiles-part-${operationId.replace("-", "").take(8)}-${item.id}"
                    )
                    TransferDatabase.setTemporaryTarget(item.id, temporary.toAppPath().toUriString())
                    storageFacade.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING)
                    check(Files.size(temporary) == attributes.size()) { "Copied size mismatch" }
                    checkControl(operationId)
                    finalizeTemporary(temporary, target)
                }
                if (action.type == SyncActionType.MOVE && !attributes.isDirectory) {
                    checkControl(operationId)
                    storageFacade.delete(source)
                }
                TransferDatabase.completeItem(item.id, action.targetUri)
                TransferDatabase.checkpoint(
                    TransferProgressCheckpoint(
                        operationId,
                        item.id,
                        attributes.size(),
                        transferred + attributes.size(),
                        action.relativePath
                    )
                )
                item.id
            }
            SyncActionType.DELETE -> {
                checkControl(operationId)
                requireNotNull(source) { "Delete path is unavailable" }
                revalidate(source, action.sourceFingerprint)
                val attributes = Files.readAttributes(
                    source,
                    java.nio.file.attribute.BasicFileAttributes::class.java,
                    LinkOption.NOFOLLOW_LINKS
                )
                val item = TransferDatabase.beginItem(
                    operationId,
                    action.sourceUri,
                    action.targetUri,
                    action.relativePath,
                    attributes.isDirectory,
                    0,
                    attributes.lastModifiedTime().toMillis().coerceAtLeast(0),
                    action.sourceFingerprint
                )
                checkControl(operationId)
                storageFacade.deleteIfExists(source)
                TransferDatabase.completeItem(item.id, action.targetUri)
                item.id
            }
            SyncActionType.CONFLICT -> error("Conflict requires user resolution")
            SyncActionType.SKIP -> 0
        }
    }

    private fun revalidate(path: Path, expected: String) {
        check(Files.exists(path, LinkOption.NOFOLLOW_LINKS)) { "Source changed or disappeared" }
        if (expected.isBlank()) return
        val attributes = Files.readAttributes(
            path,
            java.nio.file.attribute.BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS
        )
        val size = if (attributes.isDirectory) 0 else attributes.size()
        val actual = "${if (attributes.isDirectory) "d" else "f"}:$size:${attributes.lastModifiedTime().toMillis()}"
        check(expected.startsWith(actual)) { "Source changed after preview" }
    }

    private fun recoveredTargetIsValid(target: Path, sourceFingerprint: String): Boolean {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return false
        val expectedParts = sourceFingerprint.split(':')
        val expectedDirectory = expectedParts.firstOrNull() == "d"
        val expectedSize = expectedParts.getOrNull(1)?.toLongOrNull() ?: return false
        val attributes = Files.readAttributes(
            target,
            java.nio.file.attribute.BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS
        )
        return attributes.isDirectory == expectedDirectory &&
            (attributes.isDirectory || attributes.size() == expectedSize)
    }

    private fun finalizeTemporary(temporary: Path, target: Path) {
        try {
            Files.move(
                temporary,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun checkControl(operationId: String) {
        OperationControlRegistry.throwIfPauseRequested(operationId)
        when (TransferRepository.operation(operationId)?.state) {
            TransferOperationState.PAUSED -> throw PauseRequestedException()
            TransferOperationState.CANCELLED -> throw CancelRequestedException()
            else -> Unit
        }
    }

    private fun phase(action: SyncAction): Int = when (action.type) {
        SyncActionType.PROTECT -> 0
        SyncActionType.COPY, SyncActionType.UPDATE, SyncActionType.MOVE -> 1
        SyncActionType.DELETE -> 2
        SyncActionType.CONFLICT, SyncActionType.SKIP -> 3
    }

    private fun transferSize(action: SyncAction): Long =
        if (action.type in setOf(
                SyncActionType.COPY,
                SyncActionType.UPDATE,
                SyncActionType.MOVE,
                SyncActionType.PROTECT
            )
        ) {
            action.sourceFingerprint.split(':').getOrNull(1)?.toLongOrNull() ?: 0
        } else {
            0
        }
}

internal object GlobalSyncScopeLocks {
    val registry = SyncScopeLockRegistry()
}
