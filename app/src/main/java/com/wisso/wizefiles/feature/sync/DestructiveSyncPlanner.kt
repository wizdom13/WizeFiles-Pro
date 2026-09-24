// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

internal data class VersionProtectionPolicy(
    val enabled: Boolean,
    val retentionDays: Int = 30,
    val versionsPerFile: Int = 5,
    val maximumTotalBytes: Long = Long.MAX_VALUE,
    val versionsRootUri: String = ""
) {
    init {
        require(retentionDays >= 0)
        require(versionsPerFile >= 0)
        require(maximumTotalBytes >= 0)
    }
}

internal data class DestructiveSafetyLimits(
    val minimumDeletionCountForRatioGuard: Long = 20,
    val maximumDeletionRatio: Double = 0.10,
    val maximumDeletionCount: Long = 1_000,
    val maximumDeletionBytes: Long = 10L * 1024 * 1024 * 1024
)

internal data class SyncSafetyDecision(
    val allowed: Boolean,
    val reason: String = ""
)

internal object DestructiveSyncSafety {
    fun evaluate(
        deleteCount: Long,
        destinationItemCount: Long,
        deleteBytes: Long,
        scanErrors: List<String>,
        storageIdentityChanged: Boolean,
        limits: DestructiveSafetyLimits = DestructiveSafetyLimits()
    ): SyncSafetyDecision {
        if (scanErrors.isNotEmpty()) return SyncSafetyDecision(false, "SCAN_INCOMPLETE")
        if (storageIdentityChanged) return SyncSafetyDecision(false, "STORAGE_IDENTITY_CHANGED")
        if (deleteCount > limits.maximumDeletionCount) {
            return SyncSafetyDecision(false, "DELETION_COUNT_LIMIT")
        }
        if (deleteBytes > limits.maximumDeletionBytes) {
            return SyncSafetyDecision(false, "DELETION_SIZE_LIMIT")
        }
        val ratio = if (destinationItemCount == 0L) 0.0 else {
            deleteCount.toDouble() / destinationItemCount
        }
        if (
            deleteCount >= limits.minimumDeletionCountForRatioGuard &&
            ratio > limits.maximumDeletionRatio
        ) {
            return SyncSafetyDecision(false, "DELETION_RATIO_LIMIT")
        }
        return SyncSafetyDecision(true)
    }
}

internal class MirrorPlanner(
    private val comparisonPolicy: SyncComparisonPolicy,
    private val filters: SyncFilterRules = SyncFilterRules(),
    private val protection: VersionProtectionPolicy,
    private val caseSensitive: Boolean = true
) {
    fun plan(
        runId: String,
        sourceEntries: Sequence<SyncFileEntry>,
        destinationEntries: Sequence<SyncFileEntry>,
        destinationRootUri: String,
        scanErrors: List<String> = emptyList()
    ): SyncPlan {
        val sourceList = sourceEntries.toList()
        val destinationList = destinationEntries.toList()
        val update = UpdateDestinationPlanner(comparisonPolicy, filters, caseSensitive).plan(
            runId,
            sourceList.asSequence(),
            destinationList.asSequence(),
            destinationRootUri,
            scanErrors
        )
        if (scanErrors.isNotEmpty()) return update
        val sourceKeys = sourceList.filter(filters::accepts).map { key(it.normalizedRelativePath) }.toSet()
        val protectedUpdates = mutableListOf<SyncAction>()
        val destructive = mutableListOf<SyncAction>()
        var protectedItems = 0L
        var deletedItems = 0L
        var protectedBytes = 0L
        if (protection.enabled) {
            update.actions.filter {
                it.type == SyncActionType.UPDATE && it.targetFingerprint.isNotBlank()
            }.forEach { updateAction ->
                protectedUpdates += SyncAction(
                    runId = runId,
                    ordinal = protectedUpdates.size.toLong(),
                    type = SyncActionType.PROTECT,
                    direction = SyncSide.DESTINATION,
                    relativePath = updateAction.relativePath,
                    sourceUri = updateAction.targetUri,
                    targetUri = protectionTarget(runId, updateAction.relativePath),
                    sourceFingerprint = updateAction.targetFingerprint,
                    comparisonReason = "VERSION_BEFORE_UPDATE"
                )
                protectedItems++
                protectedBytes += fingerprintSize(updateAction.targetFingerprint)
            }
        }
        val destinationOnly = destinationList.filter(filters::accepts)
            .filter { key(it.normalizedRelativePath) !in sourceKeys }
        destinationOnly.filterNot(SyncFileEntry::isDirectory).forEach { destination ->
            if (protection.enabled) {
                destructive += action(
                    runId,
                    update.actions.size + destructive.size,
                    SyncActionType.PROTECT,
                    destination,
                    protectionTarget(runId, destination.normalizedRelativePath)
                )
                protectedItems++
                protectedBytes += destination.sizeBytes
            }
        }
        destinationOnly.sortedWith(
            compareBy<SyncFileEntry> { if (it.isDirectory) 1 else 0 }
                .thenByDescending { it.normalizedRelativePath.count { character -> character == '/' } }
        ).forEach { destination ->
            destructive += action(
                runId,
                update.actions.size + destructive.size,
                SyncActionType.DELETE,
                destination,
                destination.uri
            )
            deletedItems++
        }
        return SyncPlan(
            actions = (protectedUpdates + update.actions + destructive).mapIndexed { index, action ->
                action.copy(ordinal = index.toLong())
            },
            summary = update.summary.copy(
                protectedItems = protectedItems,
                deletions = deletedItems,
                protectedBytes = protectedBytes
            )
        )
    }

    private fun action(
        runId: String,
        ordinal: Int,
        type: SyncActionType,
        entry: SyncFileEntry,
        targetUri: String
    ) = SyncAction(
        runId = runId,
        ordinal = ordinal.toLong(),
        type = type,
        direction = SyncSide.DESTINATION,
        relativePath = entry.normalizedRelativePath,
        sourceUri = entry.uri,
        targetUri = targetUri,
        sourceFingerprint = entry.fingerprint(),
        comparisonReason = "DESTINATION_ONLY"
    )

    private fun key(path: String) = if (caseSensitive) path else path.lowercase()

    private fun fingerprintSize(fingerprint: String): Long =
        fingerprint.split(':').getOrNull(1)?.toLongOrNull() ?: 0

    private fun protectionTarget(runId: String, relativePath: String): String =
        SyncPathResolver.childUri(protection.versionsRootUri, "$runId/$relativePath")
}

internal class MoveSourcePlanner(
    private val comparisonPolicy: SyncComparisonPolicy,
    private val filters: SyncFilterRules = SyncFilterRules(),
    private val caseSensitive: Boolean = true
) {
    fun plan(
        runId: String,
        sourceEntries: Sequence<SyncFileEntry>,
        destinationEntries: Sequence<SyncFileEntry>,
        destinationRootUri: String,
        scanErrors: List<String> = emptyList()
    ): SyncPlan {
        val destination = destinationEntries.filter(filters::accepts)
            .associateBy { key(it.normalizedRelativePath) }
        val actions = mutableListOf<SyncAction>()
        var bytes = 0L
        var skipped = 0L
        sourceEntries.forEach { source ->
            if (!filters.accepts(source)) {
                skipped++
                return@forEach
            }
            val target = destination[key(source.normalizedRelativePath)]
            val type = when {
                source.isDirectory -> SyncActionType.SKIP
                target == null -> SyncActionType.MOVE
                SyncEntryComparator.equivalent(source, target, comparisonPolicy) -> SyncActionType.DELETE
                else -> SyncActionType.CONFLICT
            }
            if (type == SyncActionType.MOVE) bytes += source.sizeBytes
            actions += SyncAction(
                runId = runId,
                ordinal = actions.size.toLong(),
                type = type,
                direction = SyncSide.DESTINATION,
                relativePath = source.normalizedRelativePath,
                sourceUri = source.uri,
                targetUri = SyncPathResolver.childUri(
                    destinationRootUri,
                    source.normalizedRelativePath
                ),
                sourceFingerprint = source.fingerprint(),
                targetFingerprint = target?.fingerprint().orEmpty(),
                comparisonReason = when (type) {
                    SyncActionType.MOVE -> "DESTINATION_MISSING"
                    SyncActionType.DELETE -> "TARGET_ALREADY_VALIDATED"
                    SyncActionType.SKIP -> "DIRECTORY_PRESERVED"
                    else -> "DESTINATION_CONFLICT"
                },
                state = when (type) {
                    SyncActionType.CONFLICT -> SyncActionState.BLOCKED
                    SyncActionType.SKIP -> SyncActionState.SKIPPED
                    else -> SyncActionState.PENDING
                }
            )
        }
        return SyncPlan(
            actions,
            SyncPlanSummary(
                moves = actions.count { it.type == SyncActionType.MOVE }.toLong(),
                deletions = actions.count { it.type == SyncActionType.DELETE }.toLong(),
                conflicts = actions.count { it.type == SyncActionType.CONFLICT }.toLong(),
                unchangedOrSkipped = skipped,
                transferBytes = bytes
            ),
            scanErrors
        )
    }

    private fun key(path: String) = if (caseSensitive) path else path.lowercase()
}

internal data class RetainedVersion(
    val relativePath: String,
    val createdAtMillis: Long,
    val sizeBytes: Long,
    val storagePath: String = relativePath
)

internal object VersionRetentionPruner {
    fun selectForDeletion(
        versions: List<RetainedVersion>,
        policy: VersionProtectionPolicy,
        nowMillis: Long
    ): List<RetainedVersion> {
        val keepAfter = nowMillis - policy.retentionDays * 24L * 60 * 60 * 1_000
        val newestPerFile = versions.groupBy { it.relativePath }.values.flatMap { group ->
            group.sortedByDescending { it.createdAtMillis }.take(policy.versionsPerFile)
        }.toSet()
        val remove = versions.filter { it.createdAtMillis < keepAfter || it !in newestPerFile }
            .toMutableSet()
        var retainedBytes = versions.filterNot { it in remove }.sumOf { it.sizeBytes }
        versions.filterNot { it in remove }.sortedBy { it.createdAtMillis }.forEach { version ->
            if (retainedBytes > policy.maximumTotalBytes) {
                remove += version
                retainedBytes -= version.sizeBytes
            }
        }
        return remove.sortedBy { it.createdAtMillis }
    }
}
