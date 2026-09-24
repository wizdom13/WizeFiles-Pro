package com.wisso.wizefiles.feature.transfer

import com.wisso.wizefiles.storage.ConflictPolicy
import com.wisso.wizefiles.storage.FileNode
import com.wisso.wizefiles.storage.FileOperationRequest
import com.wisso.wizefiles.storage.MetadataAttribute
import com.wisso.wizefiles.storage.OperationCancellation
import com.wisso.wizefiles.storage.ProviderCapabilities
import com.wisso.wizefiles.storage.ProviderMutationCapabilities
import com.wisso.wizefiles.storage.ProviderMutationKind
import com.wisso.wizefiles.storage.TransferPlan

data class TransferPlanningRequirements(
    val metadataIntent: Set<MetadataAttribute> = emptySet(),
    val resumeRequired: Boolean = false,
    val atomicMoveRequired: Boolean = false
)

/**
 * Validates already-resolved provider nodes. Enumeration, URI/path conversion, sibling probing,
 * persistence, scheduling, and execution deliberately remain in adapters and the Android app.
 */
object TransferPlanningPolicy {
    const val MAX_REQUESTS = 100_000

    fun plan(
        requests: Iterable<FileOperationRequest>,
        conflictPolicy: ConflictPolicy,
        sourceCapabilities: ProviderCapabilities,
        destinationCapabilities: ProviderCapabilities,
        mutationCapabilities: ProviderMutationCapabilities,
        requirements: TransferPlanningRequirements = TransferPlanningRequirements(),
        cancellation: OperationCancellation = OperationCancellation.NONE
    ): TransferPlan {
        require(sourceCapabilities.canRead) { "The source provider is not readable" }
        require(destinationCapabilities.canWrite) { "The destination provider is not writable" }
        require(!requirements.resumeRequired || mutationCapabilities.supportsResume) {
            "The selected provider combination cannot resume transfers"
        }
        require(!requirements.atomicMoveRequired || destinationCapabilities.canMoveAtomically) {
            "The destination provider cannot guarantee an atomic move"
        }

        val planned = ArrayList<FileOperationRequest>()
        for (request in requests) {
            cancellation.throwIfCancelled()
            require(planned.size < MAX_REQUESTS) { "The transfer plan contains too many items" }
            validate(request, mutationCapabilities)
            planned += request
        }
        cancellation.throwIfCancelled()
        return TransferPlan(
            requests = planned,
            conflictPolicy = conflictPolicy,
            metadataIntent = requirements.metadataIntent,
            resumeRequired = requirements.resumeRequired
        )
    }

    private fun validate(
        request: FileOperationRequest,
        capabilities: ProviderMutationCapabilities
    ) {
        val kind = when (request) {
            is FileOperationRequest.Copy -> ProviderMutationKind.COPY
            is FileOperationRequest.Move -> ProviderMutationKind.MOVE
            is FileOperationRequest.Delete -> ProviderMutationKind.DELETE
        }
        require(kind in capabilities.supportedKinds) { "The provider does not support $kind" }
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

    private fun validateTarget(source: FileNode, target: FileNode, crossProvider: Boolean) {
        require(source.backendId != target.backendId || source.path != target.path) {
            "Source and destination refer to the same logical resource"
        }
        require(source.backendId == target.backendId || crossProvider) {
            "The provider does not support cross-provider transfer"
        }
    }
}
