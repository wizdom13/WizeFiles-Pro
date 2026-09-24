// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

/** Provider-neutral answer to an existing-target conflict. */
enum class ConflictPolicy {
    ASK,
    REPLACE,
    KEEP_BOTH,
    SKIP,
    ABORT
}

enum class ConflictKind {
    SAME_TYPE,
    FILE_REPLACES_DIRECTORY,
    DIRECTORY_REPLACES_FILE,
    SAME_FILE
}

sealed interface ConflictDecision {
    data object Proceed : ConflictDecision
    data object Skip : ConflictDecision
    data object Abort : ConflictDecision
    data object RequiresUserAction : ConflictDecision
    data class Rename(val candidateName: String) : ConflictDecision
    data class Reject(val reason: String) : ConflictDecision
}

/** Pure conflict policy shared by provider adapters and UI planners. */
object ConflictResolver {
    fun decide(
        policy: ConflictPolicy,
        kind: ConflictKind,
        keepBothName: String? = null
    ): ConflictDecision {
        if (kind == ConflictKind.SAME_FILE) return ConflictDecision.Skip
        if (kind != ConflictKind.SAME_TYPE && policy == ConflictPolicy.REPLACE) {
            return ConflictDecision.Reject("Replacing a file with a directory (or vice versa) is unsafe")
        }
        return when (policy) {
            ConflictPolicy.ASK -> ConflictDecision.RequiresUserAction
            ConflictPolicy.REPLACE -> ConflictDecision.Proceed
            ConflictPolicy.KEEP_BOTH -> keepBothName
                ?.takeIf(::isSafeLeafName)
                ?.let(ConflictDecision::Rename)
                ?: ConflictDecision.Reject("A safe keep-both name is required")
            ConflictPolicy.SKIP -> ConflictDecision.Skip
            ConflictPolicy.ABORT -> ConflictDecision.Abort
        }
    }

    private fun isSafeLeafName(name: String): Boolean =
        name.isNotBlank() &&
            name.length <= 255 &&
            name != "." && name != ".." &&
            name.none { it == '/' || it == '\\' || it.isISOControl() }
}

/** Shared leaf-name generation. Adapters remain responsible for probing concrete siblings. */
object KeepBothNaming {
    fun candidate(originalName: String, copyIndex: Int, isDirectory: Boolean = false): String {
        require(copyIndex >= 2) { "A keep-both suffix starts at 2" }
        require(originalName.isNotBlank() && originalName != "." && originalName != "..") {
            "A keep-both name requires a safe leaf"
        }
        require(originalName.none { it == '/' || it == '\\' || it.isISOControl() }) {
            "A keep-both name requires a safe leaf"
        }
        val extensionStart = if (isDirectory) {
            originalName.length
        } else {
            originalName.lastIndexOf('.').takeIf { it > 0 } ?: originalName.length
        }
        val candidate = buildString {
            append(originalName, 0, extensionStart)
            append(" (").append(copyIndex).append(')')
            append(originalName, extensionStart, originalName.length)
        }
        require(candidate.length <= 255) { "Generated keep-both name is too long" }
        return candidate
    }
}

/** Cooperative cancellation boundary that is independent of Android's CancellationSignal. */
fun interface OperationCancellation {
    fun isCancelled(): Boolean

    fun throwIfCancelled() {
        if (isCancelled()) throw OperationCancelledException()
    }

    companion object {
        val NONE = OperationCancellation { false }
    }
}

class OperationCancelledException : Exception("Operation was cancelled")

/** Durable, credential-free progress. Provider adapters own any opaque resume token. */
data class ResumeCheckpoint(
    val completedBytes: Long,
    val completedItems: Long,
    val opaqueToken: String? = null
) {
    init {
        require(completedBytes >= 0) { "completedBytes must not be negative" }
        require(completedItems >= 0) { "completedItems must not be negative" }
        require(opaqueToken == null || opaqueToken.length <= MAX_TOKEN_LENGTH) {
            "opaqueToken is too large"
        }
    }

    companion object {
        const val MAX_TOKEN_LENGTH = 4096
        val EMPTY = ResumeCheckpoint(0, 0)
    }
}

data class TransferPlan(
    val requests: List<FileOperationRequest>,
    val conflictPolicy: ConflictPolicy,
    val checkpoint: ResumeCheckpoint = ResumeCheckpoint.EMPTY,
    val metadataIntent: Set<MetadataAttribute> = emptySet(),
    val resumeRequired: Boolean = false
) {
    init {
        require(requests.isNotEmpty()) { "A transfer plan must contain work" }
    }
}

enum class SyncDirection { PUSH, PULL, TWO_WAY }

data class SyncPlan(
    val sourceEndpointId: String,
    val targetEndpointId: String,
    val direction: SyncDirection,
    val conflictPolicy: ConflictPolicy,
    val deleteExtraneous: Boolean = false
) {
    init {
        require(sourceEndpointId.isNotBlank()) { "sourceEndpointId must not be blank" }
        require(targetEndpointId.isNotBlank()) { "targetEndpointId must not be blank" }
        require(sourceEndpointId != targetEndpointId) { "A sync plan requires distinct endpoints" }
        require(!(direction == SyncDirection.TWO_WAY && deleteExtraneous)) {
            "Two-way sync cannot safely delete extraneous entries"
        }
    }
}
