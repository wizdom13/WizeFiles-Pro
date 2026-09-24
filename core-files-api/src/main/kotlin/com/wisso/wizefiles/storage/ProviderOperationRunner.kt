package com.wisso.wizefiles.storage

import java.io.Closeable

sealed interface ProviderMutationOutcome {
    data class Complete(
        val completedBytes: Long,
        val completedItems: Long,
        val metadata: MetadataPreservationReport = MetadataPreservationReport(emptyList())
    ) : ProviderMutationOutcome

    data class Cancelled(
        val completedBytes: Long = 0,
        val completedItems: Long = 0,
        val destinationMayExist: Boolean = false,
        val message: String? = null
    ) : ProviderMutationOutcome

    data class Failed(
        val signal: ProviderFailureSignal,
        val completedBytes: Long = 0,
        val completedItems: Long = 0,
        val resumeToken: String? = null,
        val destinationMayExist: Boolean = false,
        val message: String? = null
    ) : ProviderMutationOutcome
}

enum class ProviderMutationKind { COPY, MOVE, DELETE }

data class ProviderMutationCapabilities(
    val supportedKinds: Set<ProviderMutationKind>,
    val supportsResume: Boolean = false,
    val supportsCrossProviderCopy: Boolean = false,
    val supportsCrossProviderMove: Boolean = false
) {
    init {
        require(supportedKinds.isNotEmpty()) { "A provider mutation port must support an operation" }
    }
}

/** Minimal mutation port implemented by local and remote provider adapters.
 *
 * Adapters must check [cancellation] before every blocking call and between committed chunks.
 * If cancellation wins before the final commit they return [ProviderMutationOutcome.Cancelled].
 * Once the requested mutation is durably committed they return [ProviderMutationOutcome.Complete],
 * even if cancellation is observed immediately afterward.
 */
interface ProviderMutationPort : Closeable {
    val capabilities: ProviderMutationCapabilities

    fun execute(
        request: FileOperationRequest,
        checkpoint: ResumeCheckpoint,
        cancellation: OperationCancellation
    ): ProviderMutationOutcome
}

/** Enforces cleanup, cancellation, failure mapping, and partial-result invariants for every adapter. */
object ProviderOperationRunner {
    fun run(
        port: ProviderMutationPort,
        request: FileOperationRequest,
        checkpoint: ResumeCheckpoint = ResumeCheckpoint.EMPTY,
        cancellation: OperationCancellation = OperationCancellation.NONE
    ): OperationResult {
        port.use {
            validateRequest(it.capabilities, request, checkpoint)
            cancellation.throwIfCancelled()
            return when (val outcome = it.execute(request, checkpoint, cancellation)) {
                is ProviderMutationOutcome.Complete -> OperationResult.Complete(
                    completedBytes = outcome.completedBytes.also {
                        require(it >= checkpoint.completedBytes) { "Provider progress moved backwards" }
                    },
                    completedItems = outcome.completedItems.also {
                        require(it >= checkpoint.completedItems) { "Provider item progress moved backwards" }
                    },
                    metadata = outcome.metadata
                )
                is ProviderMutationOutcome.Cancelled -> {
                    requireProgressNotBehindCheckpoint(
                        outcome.completedBytes,
                        outcome.completedItems,
                        checkpoint
                    )
                    OperationResult.Partial(
                        completedBytes = outcome.completedBytes,
                        completedItems = outcome.completedItems,
                        failure = ProviderFailureMapper.map(
                            ProviderFailureSignal.INTERRUPTED,
                            outcome.message,
                            mutationStarted = outcome.completedBytes > checkpoint.completedBytes ||
                                outcome.completedItems > checkpoint.completedItems ||
                                outcome.destinationMayExist
                        ),
                        checkpoint = null,
                        destinationMayExist = outcome.destinationMayExist
                    )
                }
                is ProviderMutationOutcome.Failed -> {
                    requireProgressNotBehindCheckpoint(
                        outcome.completedBytes,
                        outcome.completedItems,
                        checkpoint
                    )
                    val failure = ProviderFailureMapper.map(
                        outcome.signal,
                        outcome.message,
                        mutationStarted = outcome.completedBytes > checkpoint.completedBytes ||
                            outcome.completedItems > checkpoint.completedItems || outcome.destinationMayExist
                    )
                    val resume = if (failure.retryClassification == RetryClassification.TRANSIENT) {
                        ResumeCheckpoint(outcome.completedBytes, outcome.completedItems, outcome.resumeToken)
                    } else null
                    OperationResult.Partial(
                        completedBytes = outcome.completedBytes,
                        completedItems = outcome.completedItems,
                        failure = failure,
                        checkpoint = resume,
                        destinationMayExist = outcome.destinationMayExist
                    )
                }
            }
        }
    }

    private fun validateRequest(
        capabilities: ProviderMutationCapabilities,
        request: FileOperationRequest,
        checkpoint: ResumeCheckpoint
    ) {
        validateNode(request.source)
        val kind = when (request) {
            is FileOperationRequest.Copy -> ProviderMutationKind.COPY
            is FileOperationRequest.Move -> ProviderMutationKind.MOVE
            is FileOperationRequest.Delete -> ProviderMutationKind.DELETE
        }
        require(kind in capabilities.supportedKinds) {
            "Provider does not support ${kind.name.lowercase()} mutations"
        }
        require(checkpoint == ResumeCheckpoint.EMPTY || capabilities.supportsResume) {
            "Provider does not support resumable mutations"
        }
        when (request) {
            is FileOperationRequest.Copy -> validateTarget(
                request.source,
                request.target,
                capabilities.supportsCrossProviderCopy
            )
            is FileOperationRequest.Move -> validateTarget(
                request.source,
                request.target,
                capabilities.supportsCrossProviderMove
            )
            is FileOperationRequest.Delete -> Unit
        }
    }

    private fun validateTarget(source: FileNode, target: FileNode, supportsCrossProvider: Boolean) {
        validateNode(target)
        require(source.backendId != target.backendId || source.path != target.path) {
            "Source and target refer to the same provider node"
        }
        require(source.backendId == target.backendId || supportsCrossProvider) {
            "Provider port does not support cross-provider mutations"
        }
    }

    private fun validateNode(node: FileNode) {
        require(node.backendId.isNotBlank() && node.backendId.length <= 128) {
            "Provider backendId is invalid"
        }
        require(node.path.isNotBlank() && node.path.length <= 8192 && '\u0000' !in node.path) {
            "Provider path is invalid"
        }
        require(node.name.isNotBlank() && node.name.length <= 255 && '\u0000' !in node.name) {
            "Provider node name is invalid"
        }
    }

    private fun requireProgressNotBehindCheckpoint(
        completedBytes: Long,
        completedItems: Long,
        checkpoint: ResumeCheckpoint
    ) {
        require(
            completedBytes >= checkpoint.completedBytes &&
                completedItems >= checkpoint.completedItems
        ) {
            "Provider partial progress moved backwards"
        }
    }
}
