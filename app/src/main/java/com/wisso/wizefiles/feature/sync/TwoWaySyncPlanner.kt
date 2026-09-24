// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class SideChange {
    UNCHANGED,
    ADDED,
    MODIFIED,
    DELETED
}

internal class TwoWaySyncPlanner(
    private val comparisonPolicy: SyncComparisonPolicy,
    private val conflictPolicy: SyncConflictPolicy,
    private val propagateDeletions: Boolean,
    private val caseSensitive: Boolean = true,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    fun plan(
        runId: String,
        currentSource: Sequence<SyncFileEntry>,
        currentDestination: Sequence<SyncFileEntry>,
        baselineSource: Sequence<SyncFileEntry>,
        baselineDestination: Sequence<SyncFileEntry>,
        sourceRootUri: String,
        destinationRootUri: String,
        scanErrors: List<String> = emptyList()
    ): SyncPlan {
        if (scanErrors.isNotEmpty()) {
            return SyncPlan(emptyList(), SyncPlanSummary(), scanErrors)
        }
        val a = currentSource.associateBy { key(it.normalizedRelativePath) }
        val b = currentDestination.associateBy { key(it.normalizedRelativePath) }
        val baseA = baselineSource.associateBy { key(it.normalizedRelativePath) }
        val baseB = baselineDestination.associateBy { key(it.normalizedRelativePath) }
        val paths = (a.keys + b.keys + baseA.keys + baseB.keys).sorted()
        val actions = mutableListOf<SyncAction>()
        paths.forEach { pathKey ->
            val currentA = a[pathKey]
            val currentB = b[pathKey]
            val previousA = baseA[pathKey]
            val previousB = baseB[pathKey]
            val changeA = change(previousA, currentA)
            val changeB = change(previousB, currentB)
            when {
                changeA == SideChange.UNCHANGED && changeB == SideChange.UNCHANGED ->
                    addSkip(actions, runId, pathKey, currentA ?: currentB)
                changeA != SideChange.UNCHANGED && changeB == SideChange.UNCHANGED ->
                    applyOneSideChange(
                        actions, runId, pathKey, changeA, currentA, currentB,
                        SyncSide.DESTINATION, destinationRootUri
                    )
                changeB != SideChange.UNCHANGED && changeA == SideChange.UNCHANGED ->
                    applyOneSideChange(
                        actions, runId, pathKey, changeB, currentB, currentA,
                        SyncSide.SOURCE, sourceRootUri
                    )
                currentA == null && currentB == null -> Unit
                currentA != null && currentB != null &&
                    SyncEntryComparator.equivalent(currentA, currentB, comparisonPolicy) ->
                    addSkip(actions, runId, pathKey, currentA)
                else -> resolveConflict(
                    actions, runId, pathKey, currentA, currentB, sourceRootUri, destinationRootUri
                )
            }
        }
        return SyncPlan(actions, summarize(actions), scanErrors)
    }

    private fun change(previous: SyncFileEntry?, current: SyncFileEntry?): SideChange = when {
        previous == null && current == null -> SideChange.UNCHANGED
        previous == null -> SideChange.ADDED
        current == null -> SideChange.DELETED
        SyncEntryComparator.equivalent(previous, current, comparisonPolicy) -> SideChange.UNCHANGED
        else -> SideChange.MODIFIED
    }

    private fun applyOneSideChange(
        actions: MutableList<SyncAction>,
        runId: String,
        path: String,
        change: SideChange,
        changedEntry: SyncFileEntry?,
        otherEntry: SyncFileEntry?,
        targetSide: SyncSide,
        targetRoot: String
    ) {
        if (change == SideChange.DELETED) {
            if (propagateDeletions && otherEntry != null) {
                actions += action(
                    actions, runId, SyncActionType.DELETE, targetSide, path,
                    otherEntry.uri, otherEntry.uri, otherEntry, "DELETION_PROPAGATED"
                )
            } else {
                addSkip(actions, runId, path, otherEntry)
            }
            return
        }
        val entry = changedEntry ?: return
        actions += action(
            actions,
            runId,
            if (otherEntry == null) SyncActionType.COPY else SyncActionType.UPDATE,
            targetSide,
            path,
            entry.uri,
            resolve(targetRoot, entry.normalizedRelativePath),
            entry,
            if (otherEntry == null) "ADDED_ON_OTHER_SIDE" else "CHANGED_ON_OTHER_SIDE"
        ).copy(targetFingerprint = otherEntry?.fingerprint().orEmpty())
    }

    private fun resolveConflict(
        actions: MutableList<SyncAction>,
        runId: String,
        path: String,
        source: SyncFileEntry?,
        destination: SyncFileEntry?,
        sourceRoot: String,
        destinationRoot: String
    ) {
        if (source == null || destination == null) {
            actions += conflict(
                actions, runId, path, source, destination, "DELETE_MODIFY_CONFLICT"
            )
            return
        }
        when (conflictPolicy) {
            SyncConflictPolicy.PREFER_SOURCE -> source?.let {
                actions += copy(
                    actions, runId, path, it, destination, SyncSide.DESTINATION,
                    destinationRoot, "CONFLICT_PREFER_SOURCE"
                )
            }
            SyncConflictPolicy.PREFER_DESTINATION -> destination?.let {
                actions += copy(
                    actions, runId, path, it, source, SyncSide.SOURCE,
                    sourceRoot, "CONFLICT_PREFER_DESTINATION"
                )
            }
            SyncConflictPolicy.PREFER_NEWER -> prefer(
                actions, runId, path, source, destination, sourceRoot, destinationRoot
            ) { first, second -> first.modifiedAtMillis >= second.modifiedAtMillis }
            SyncConflictPolicy.PREFER_LARGER -> prefer(
                actions, runId, path, source, destination, sourceRoot, destinationRoot
            ) { first, second -> first.sizeBytes >= second.sizeBytes }
            SyncConflictPolicy.KEEP_BOTH -> {
                val conflictPath = conflictName(path, "destination")
                actions += action(
                    actions, runId, SyncActionType.COPY, SyncSide.SOURCE,
                    conflictPath, destination.uri, resolve(sourceRoot, conflictPath),
                    destination, "CONFLICT_KEEP_BOTH"
                )
                actions += copy(
                    actions, runId, path, source, destination, SyncSide.DESTINATION,
                    destinationRoot, "CONFLICT_KEEP_BOTH"
                )
            }
            SyncConflictPolicy.SKIP -> addSkip(actions, runId, path, source ?: destination)
            SyncConflictPolicy.ASK ->
                actions += conflict(actions, runId, path, source, destination, "USER_DECISION_REQUIRED")
        }
    }

    private fun prefer(
        actions: MutableList<SyncAction>,
        runId: String,
        path: String,
        source: SyncFileEntry?,
        destination: SyncFileEntry?,
        sourceRoot: String,
        destinationRoot: String,
        chooseSource: (SyncFileEntry, SyncFileEntry) -> Boolean
    ) {
        if (source == null || destination == null) {
            actions += conflict(actions, runId, path, source, destination, "DELETE_MODIFY_CONFLICT")
        } else if (chooseSource(source, destination)) {
            actions += copy(
                actions, runId, path, source, destination, SyncSide.DESTINATION,
                destinationRoot, "CONFLICT_POLICY"
            )
        } else {
            actions += copy(
                actions, runId, path, destination, source, SyncSide.SOURCE,
                sourceRoot, "CONFLICT_POLICY"
            )
        }
    }

    private fun copy(
        actions: List<SyncAction>,
        runId: String,
        path: String,
        entry: SyncFileEntry,
        targetEntry: SyncFileEntry?,
        side: SyncSide,
        root: String,
        reason: String
    ) = action(
        actions, runId, SyncActionType.UPDATE, side, path,
        entry.uri, resolve(root, path), entry, reason
    ).copy(targetFingerprint = targetEntry?.fingerprint().orEmpty())

    private fun conflict(
        actions: List<SyncAction>,
        runId: String,
        path: String,
        source: SyncFileEntry?,
        destination: SyncFileEntry?,
        reason: String
    ) = SyncAction(
        runId = runId,
        ordinal = actions.size.toLong(),
        type = SyncActionType.CONFLICT,
        direction = SyncSide.DESTINATION,
        relativePath = path,
        sourceUri = source?.uri.orEmpty(),
        targetUri = destination?.uri.orEmpty(),
        sourceFingerprint = source?.fingerprint().orEmpty(),
        targetFingerprint = destination?.fingerprint().orEmpty(),
        comparisonReason = reason,
        state = SyncActionState.BLOCKED
    )

    private fun addSkip(
        actions: MutableList<SyncAction>,
        runId: String,
        path: String,
        entry: SyncFileEntry?
    ) {
        actions += action(
            actions, runId, SyncActionType.SKIP, SyncSide.DESTINATION,
            path, entry?.uri.orEmpty(), entry?.uri.orEmpty(), entry, "UNCHANGED"
        ).copy(state = SyncActionState.SKIPPED)
    }

    private fun action(
        actions: List<SyncAction>,
        runId: String,
        type: SyncActionType,
        side: SyncSide,
        path: String,
        sourceUri: String,
        targetUri: String,
        entry: SyncFileEntry?,
        reason: String
    ) = SyncAction(
        runId = runId,
        ordinal = actions.size.toLong(),
        type = type,
        direction = side,
        relativePath = path,
        sourceUri = sourceUri,
        targetUri = targetUri,
        sourceFingerprint = entry?.fingerprint().orEmpty(),
        comparisonReason = reason
    )

    private fun summarize(actions: List<SyncAction>) = SyncPlanSummary(
        copiesToDestination = actions.count { it.type in setOf(SyncActionType.COPY, SyncActionType.UPDATE) && it.direction == SyncSide.DESTINATION }.toLong(),
        copiesToSource = actions.count { it.type in setOf(SyncActionType.COPY, SyncActionType.UPDATE) && it.direction == SyncSide.SOURCE }.toLong(),
        deletions = actions.count { it.type == SyncActionType.DELETE }.toLong(),
        conflicts = actions.count { it.type == SyncActionType.CONFLICT }.toLong(),
        unchangedOrSkipped = actions.count { it.type == SyncActionType.SKIP }.toLong(),
        transferBytes = actions.filter { it.type == SyncActionType.COPY || it.type == SyncActionType.UPDATE }
            .sumOf { action ->
                val marker = action.sourceFingerprint.split(':').getOrNull(1)
                marker?.toLongOrNull() ?: 0
            }
    )

    private fun conflictName(path: String, side: String): String {
        val slash = path.lastIndexOf('/')
        val directory = if (slash >= 0) path.substring(0, slash + 1) else ""
        val name = if (slash >= 0) path.substring(slash + 1) else path
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        val stamp = CONFLICT_TIME_FORMAT.format(Instant.ofEpochMilli(nowMillis()).atZone(ZoneId.systemDefault()))
        return directory + name.substring(0, dot) + " (conflict from $side $stamp)" + name.substring(dot)
    }

    private fun key(path: String) = if (caseSensitive) path else path.lowercase()

    private fun resolve(root: String, path: String) = SyncPathResolver.childUri(root, path)

    companion object {
        private val CONFLICT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm")
    }
}
