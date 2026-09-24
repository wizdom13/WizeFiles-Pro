package com.wisso.wizefiles.provider.archive.editor

import java.util.UUID

enum class ArchiveMutationType {
    ADD,
    DELETE,
    RENAME,
    CREATE_DIRECTORY
}

enum class ArchiveConflictPolicy {
    FAIL,
    REPLACE,
    KEEP_BOTH,
    SKIP
}

enum class ArchiveEditPhase {
    PLANNING,
    REBUILDING,
    VALIDATING,
    COMMITTING,
    COMMITTED,
    COMPLETED
}

data class ArchiveMutation(
    val type: ArchiveMutationType,
    val path: String,
    val targetPath: String = "",
    val sourceUri: String = "",
    val deleteSourceAfterCommit: Boolean = false
)

data class ArchiveMutationSpec(
    val operationId: String = UUID.randomUUID().toString(),
    val archiveUri: String,
    val mutations: List<ArchiveMutation>,
    val conflictPolicy: ArchiveConflictPolicy = ArchiveConflictPolicy.FAIL,
    val originalFingerprint: String = "",
    val phase: ArchiveEditPhase = ArchiveEditPhase.PLANNING
) {
    init {
        require(archiveUri.isNotBlank())
        require(mutations.isNotEmpty())
    }
}

data class ArchiveNamespaceEntry(
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0
)

data class PlannedArchiveEntry(
    val originalPath: String?,
    val finalPath: String,
    val isDirectory: Boolean,
    val sourceUri: String = "",
    val size: Long = 0,
    val deleteSourceAfterCommit: Boolean = false
) {
    val isAddition: Boolean get() = originalPath == null
}

data class ArchiveMutationPlan(
    val entries: List<PlannedArchiveEntry>,
    val skippedPaths: List<String>
) {
    val finalPaths: Set<String> = entries.mapTo(linkedSetOf()) { it.finalPath }
    val totalBytes: Long = entries.sumOf { it.size.coerceAtLeast(0) }
}
