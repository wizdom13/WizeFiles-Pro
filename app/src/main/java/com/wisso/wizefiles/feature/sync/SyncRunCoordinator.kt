package com.wisso.wizefiles.feature.sync

import com.wisso.wizefiles.core.entitlement.ProFeature
import com.wisso.wizefiles.core.entitlement.ProFeatureAccess

internal data class PlannedSyncRun(
    val run: SyncRun,
    val plan: SyncPlan,
    val safetyDecision: SyncSafetyDecision
)

internal enum class ScheduledSyncDisposition {
    EXECUTE,
    NEEDS_ATTENTION,
    SAFETY_BLOCKED
}

internal fun scheduledSyncDisposition(
    runState: SyncRunState,
    safetyAllowed: Boolean,
    deletions: Long,
    conflicts: Long,
    askBeforeResolvingConflicts: Boolean
): ScheduledSyncDisposition = when {
    runState == SyncRunState.SAFETY_BLOCKED || (!safetyAllowed && deletions > 0) ->
        ScheduledSyncDisposition.SAFETY_BLOCKED
    conflicts > 0 && askBeforeResolvingConflicts -> ScheduledSyncDisposition.NEEDS_ATTENTION
    else -> ScheduledSyncDisposition.EXECUTE
}

internal class SyncRunCoordinator(
    private val scanner: SyncTreeScanner = SyncTreeScanner(),
    private val executor: SyncActionExecutor = SyncActionExecutor()
) {
    fun plan(profileId: String, trigger: SyncRunTrigger): PlannedSyncRun {
        ProFeatureAccess.require(ProFeature.SYNC_PROFILES)
        val profile = requireNotNull(SyncRepository.profile(profileId))
        SyncDomainPolicy.validate(profile)
        checkNoActiveRun(profileId)
        val source = scanner.scan(profile.sourceUri)
        val destination = scanner.scan(profile.destinationUri)
        val endpointValidation = SyncEndpointValidator.validate(
            SyncEndpoint(profile.sourceUri, source.storageIdentity, writable = true, available = source.complete),
            SyncEndpoint(profile.destinationUri, destination.storageIdentity, writable = true, available = destination.complete)
        )
        val endpointError = (endpointValidation as? SyncEndpointValidation.Invalid)?.reason
        val errors = source.errors + destination.errors + listOfNotNull(endpointError)
        val scanDiagnostics = encodeSyncScanDiagnostics(source, destination, endpointError)
        val run = SyncRepository.createRun(
            SyncRun(
                profileId = profile.id,
                trigger = trigger,
                baselineBefore = profile.baselineGeneration
            )
        )
        val protection = protection(profile)
        val filters = SyncFilterCodec.decode(profile.filtersJson)
        val plan = when (profile.mode) {
            SyncMode.UPDATE_DESTINATION -> UpdateDestinationPlanner(
                profile.comparisonPolicy,
                filters,
                providerCaseSensitive(profile.destinationUri)
            ).plan(
                run.id,
                source.entries.asSequence().filter(filters::accepts),
                destination.entries.asSequence().filter(filters::accepts),
                profile.destinationUri,
                errors
            )
            SyncMode.MIRROR -> MirrorPlanner(
                profile.comparisonPolicy,
                filters,
                protection = protection,
                caseSensitive = providerCaseSensitive(profile.destinationUri)
            ).plan(
                run.id,
                source.entries.asSequence(),
                destination.entries.asSequence(),
                profile.destinationUri,
                errors
            )
            SyncMode.MOVE_SOURCE -> MoveSourcePlanner(
                profile.comparisonPolicy,
                filters,
                providerCaseSensitive(profile.destinationUri)
            ).plan(
                run.id,
                source.entries.asSequence(),
                destination.entries.asSequence(),
                profile.destinationUri,
                errors
            )
            SyncMode.TWO_WAY -> TwoWaySyncPlanner(
                profile.comparisonPolicy,
                profile.conflictPolicy,
                profile.propagateDeletions,
                caseSensitive = providerCaseSensitive(profile.destinationUri)
            ).plan(
                run.id,
                source.entries.asSequence().filter(filters::accepts),
                destination.entries.asSequence().filter(filters::accepts),
                baseline(profile, SyncSide.SOURCE, profile.sourceUri),
                baseline(profile, SyncSide.DESTINATION, profile.destinationUri),
                profile.sourceUri,
                profile.destinationUri,
                errors
            )
        }
        val protectedPlan = if (profile.mode == SyncMode.TWO_WAY && protection.enabled) {
            addTwoWayProtection(run.id, plan, profile, protection)
        } else {
            plan
        }
        SyncRepository.replaceActions(run.id, protectedPlan.actions)
        var previewRun = SyncRepository.markRunPreviewReady(run.id, protectedPlan.summary)
        val deleteActions = protectedPlan.actions.filter { it.type == SyncActionType.DELETE }
        val unexpectedlyEmptySource = profile.baselineGeneration > 0 &&
            source.entries.isEmpty() && SyncRepository.snapshotEntries(
                profile.id,
                profile.baselineGeneration,
                SyncSide.SOURCE
            ).isNotEmpty()
        val safety = if (unexpectedlyEmptySource) {
            SyncSafetyDecision(false, "SOURCE_UNEXPECTEDLY_EMPTY")
        } else {
            DestructiveSyncSafety.evaluate(
                deleteCount = deleteActions.size.toLong(),
                destinationItemCount = destination.entries.size.toLong(),
                deleteBytes = deleteActions.sumOf { fingerprintSize(it.sourceFingerprint) },
                scanErrors = errors,
                storageIdentityChanged = storageIdentityChanged(profile, source, destination)
            )
        }
        if (errors.isNotEmpty() || safety.reason in setOf(
                "STORAGE_IDENTITY_CHANGED",
                "SOURCE_UNEXPECTEDLY_EMPTY"
            )
        ) {
            previewRun = SyncRepository.transitionRun(
                runId = previewRun.id,
                state = SyncRunState.SAFETY_BLOCKED,
                safetyBlockReason = if (errors.isNotEmpty()) {
                    "SCAN_INCOMPLETE"
                } else {
                    safety.reason
                },
                safetyBlockDetails = if (errors.isNotEmpty()) scanDiagnostics else ""
            )
        }
        return PlannedSyncRun(previewRun, protectedPlan, safety)
    }

    fun approveAndExecute(runId: String): SyncExecutionResult {
        val approved = approve(runId)
        return executeApproved(approved)
    }

    fun approve(runId: String): SyncRun {
        val run = requireNotNull(SyncRepository.run(runId))
        require(run.state == SyncRunState.PREVIEW_READY || run.state == SyncRunState.SAFETY_BLOCKED)
        require(run.safetyBlockReason !in NON_OVERRIDABLE_BLOCKS) {
            "The storage scan or identity check must succeed before this run can start"
        }
        return SyncRepository.transitionRun(run.id, SyncRunState.APPROVED)
    }

    fun executeApproved(run: SyncRun): SyncExecutionResult {
        require(run.state == SyncRunState.APPROVED)
        return executeReady(run)
    }

    fun executeQueued(run: SyncRun): SyncExecutionResult {
        require(run.state == SyncRunState.QUEUED)
        return executeReady(run)
    }

    private fun executeReady(run: SyncRun): SyncExecutionResult {
        val profile = requireNotNull(SyncRepository.profile(run.profileId))
        val result = executor.execute(profile, run)
        if (!result.paused && !result.cancelled && result.failed == 0 && result.blocked == 0) {
            commitBaseline(profile, run.id)
        }
        return result
    }

    fun resume(runId: String): SyncExecutionResult {
        val run = requireNotNull(SyncRepository.run(runId))
        require(run.state == SyncRunState.PAUSED || run.state == SyncRunState.FAILED ||
            run.state == SyncRunState.COMPLETED_WITH_WARNINGS)
        val profile = requireNotNull(SyncRepository.profile(run.profileId))
        check(
            SyncRepository.runs(profile.id).none {
                it.id != run.id && !it.state.isTerminal
            }
        ) { "A newer sync run is already active for this profile" }
        val result = executor.execute(profile, run)
        if (!result.paused && !result.cancelled && result.failed == 0 && result.blocked == 0) {
            commitBaseline(profile, run.id)
        }
        return result
    }

    fun runScheduled(profileId: String): SyncWorkerResult {
        if (!ProFeatureAccess.isAllowed(ProFeature.SCHEDULED_SYNC)) {
            return SyncWorkerResult.SUCCESS
        }
        val planned = plan(profileId, SyncRunTrigger.SCHEDULED)
        val profile = requireNotNull(SyncRepository.profile(profileId))
        if (profile.baselineGeneration == 0L) return SyncWorkerResult.SUCCESS
        when (
            scheduledSyncDisposition(
                runState = planned.run.state,
                safetyAllowed = planned.safetyDecision.allowed,
                deletions = planned.plan.summary.deletions,
                conflicts = planned.plan.summary.conflicts,
                askBeforeResolvingConflicts = profile.conflictPolicy == SyncConflictPolicy.ASK
            )
        ) {
            ScheduledSyncDisposition.SAFETY_BLOCKED -> {
                if (planned.run.state != SyncRunState.SAFETY_BLOCKED) {
                    SyncRepository.transitionRun(
                        planned.run.id,
                        SyncRunState.SAFETY_BLOCKED,
                        planned.safetyDecision.reason
                    )
                }
                return SyncWorkerResult.SUCCESS
            }
            ScheduledSyncDisposition.NEEDS_ATTENTION -> {
                val approved = approve(planned.run.id)
                SyncRepository.transitionRun(approved.id, SyncRunState.QUEUED)
                SyncRepository.transitionRun(approved.id, SyncRunState.RUNNING)
                SyncRepository.transitionRun(approved.id, SyncRunState.NEEDS_ATTENTION)
                return SyncWorkerResult.SUCCESS
            }
            ScheduledSyncDisposition.EXECUTE -> Unit
        }
        return runCatching {
            val result = approveAndExecute(planned.run.id)
            if (result.paused || result.cancelled || result.failed == 0) {
                SyncWorkerResult.SUCCESS
            } else {
                SyncWorkerResult.RETRY
            }
        }.getOrDefault(SyncWorkerResult.RETRY)
    }

    private fun commitBaseline(profile: SyncProfile, runId: String) {
        val source = scanner.scan(profile.sourceUri)
        val destination = scanner.scan(profile.destinationUri)
        if (!source.complete || !destination.complete) return
        val generation = profile.baselineGeneration + 1
        val baselineFilter = SyncFilterCodec.decode(profile.filtersJson)
        SyncRepository.commitBaseline(
            runId,
            source.copy(entries = source.entries.filter(baselineFilter::accepts))
                .toSnapshots(profile.id, generation, SyncSide.SOURCE),
            destination.copy(entries = destination.entries.filter(baselineFilter::accepts))
                .toSnapshots(profile.id, generation, SyncSide.DESTINATION)
        )
        pruneVersions(profile, profile.destinationUri)
        if (profile.mode == SyncMode.TWO_WAY) pruneVersions(profile, profile.sourceUri)
    }

    private fun baseline(
        profile: SyncProfile,
        side: SyncSide,
        rootUri: String
    ): Sequence<SyncFileEntry> = if (profile.baselineGeneration == 0L) {
        emptySequence()
    } else {
        SyncRepository.snapshotEntries(profile.id, profile.baselineGeneration, side)
            .asSequence().map { it.toFileEntry(rootUri) }
    }

    private fun checkNoActiveRun(profileId: String) {
        check(SyncRepository.runs(profileId).none { !it.state.isTerminal }) {
            "A sync run is already active for this profile"
        }
    }

    private fun storageIdentityChanged(
        profile: SyncProfile,
        source: SyncScanResult,
        destination: SyncScanResult
    ): Boolean {
        if (profile.baselineGeneration == 0L) return false
        val previousSource = SyncRepository.snapshotEntries(
            profile.id, profile.baselineGeneration, SyncSide.SOURCE
        ).firstOrNull()?.providerIdentity
        val previousDestination = SyncRepository.snapshotEntries(
            profile.id, profile.baselineGeneration, SyncSide.DESTINATION
        ).firstOrNull()?.providerIdentity
        return previousSource != null && previousSource != source.storageIdentity ||
            previousDestination != null && previousDestination != destination.storageIdentity
    }

    private fun providerCaseSensitive(uri: String): Boolean =
        DefaultSyncProviderCapabilityResolver.capabilities(uri).caseSensitive

    private fun protection(profile: SyncProfile): VersionProtectionPolicy {
        val enabled = profile.protectionJson.contains("\"enabled\":true")
        return VersionProtectionPolicy(
            enabled = enabled,
            versionsRootUri = SyncPathResolver.childUri(
                profile.destinationUri,
                ".wizefiles-versions/${profile.id}"
            )
        )
    }

    private fun addTwoWayProtection(
        runId: String,
        plan: SyncPlan,
        profile: SyncProfile,
        policy: VersionProtectionPolicy
    ): SyncPlan {
        check(policy.enabled)
        val output = mutableListOf<SyncAction>()
        var protected = 0L
        var protectedBytes = 0L
        plan.actions.forEach { action ->
            if (action.type == SyncActionType.CONFLICT) {
                listOf(
                    Triple(SyncSide.SOURCE, action.sourceUri, action.sourceFingerprint),
                    Triple(SyncSide.DESTINATION, action.targetUri, action.targetFingerprint)
                ).forEach { (side, existingUri, existingFingerprint) ->
                    if (existingUri.isNotBlank() && existingFingerprint.isNotBlank()) {
                        val root = if (side == SyncSide.SOURCE) {
                            profile.sourceUri
                        } else {
                            profile.destinationUri
                        }
                        output += SyncAction(
                            runId = runId,
                            ordinal = output.size.toLong(),
                            type = SyncActionType.PROTECT,
                            direction = side,
                            relativePath = action.relativePath,
                            sourceUri = existingUri,
                            targetUri = SyncPathResolver.childUri(
                                root,
                                ".wizefiles-versions/${profile.id}/$runId/${action.relativePath}"
                            ),
                            sourceFingerprint = existingFingerprint,
                            comparisonReason = "VERSION_BEFORE_CONFLICT"
                        )
                        protected++
                        protectedBytes += fingerprintSize(existingFingerprint)
                    }
                }
            }
            if (action.type == SyncActionType.DELETE || action.type == SyncActionType.UPDATE) {
                val existingUri = if (action.type == SyncActionType.DELETE) {
                    action.sourceUri
                } else {
                    action.targetUri
                }
                val existingFingerprint = if (action.type == SyncActionType.DELETE) {
                    action.sourceFingerprint
                } else {
                    action.targetFingerprint
                }
                if (existingUri.isNotBlank() && existingFingerprint.isNotBlank()) {
                    val root = if (action.direction == SyncSide.SOURCE) {
                        profile.sourceUri
                    } else {
                        profile.destinationUri
                    }
                    output += SyncAction(
                        runId = runId,
                        ordinal = output.size.toLong(),
                        type = SyncActionType.PROTECT,
                        direction = action.direction,
                        relativePath = action.relativePath,
                        sourceUri = existingUri,
                        targetUri = SyncPathResolver.childUri(
                            root,
                            ".wizefiles-versions/${profile.id}/$runId/${action.relativePath}"
                        ),
                        sourceFingerprint = existingFingerprint,
                        comparisonReason = "VERSION_PROTECTION"
                    )
                    protected++
                    protectedBytes += fingerprintSize(existingFingerprint)
                }
            }
            output += action.copy(ordinal = output.size.toLong())
        }
        return plan.copy(
            actions = output,
            summary = plan.summary.copy(
                protectedItems = plan.summary.protectedItems + protected,
                protectedBytes = plan.summary.protectedBytes + protectedBytes
            )
        )
    }

    private fun fingerprintSize(fingerprint: String): Long =
        fingerprint.split(':').getOrNull(1)?.toLongOrNull() ?: 0

    companion object {
        private val NON_OVERRIDABLE_BLOCKS = setOf("SCAN_INCOMPLETE", "STORAGE_IDENTITY_CHANGED")
    }

    private fun pruneVersions(profile: SyncProfile, rootUri: String) {
        val policy = protection(profile)
        if (!policy.enabled) return
        val versionsRoot = SyncPathResolver.childUri(
            rootUri,
            ".wizefiles-versions/${profile.id}"
        )
        val scan = scanner.scan(versionsRoot)
        if (!scan.complete) return
        val files = scan.entries.filterNot(SyncFileEntry::isDirectory)
        val runTimes = SyncRepository.runs(profile.id).associate { run ->
            run.id to (run.completedAtMillis.takeIf { it > 0 } ?: run.createdAtMillis)
        }
        val retained = files.map {
            val versionRunId = it.normalizedRelativePath.substringBefore('/')
            RetainedVersion(
                relativePath = it.normalizedRelativePath.substringAfter('/'),
                createdAtMillis = runTimes[versionRunId] ?: it.modifiedAtMillis,
                sizeBytes = it.sizeBytes,
                storagePath = it.normalizedRelativePath
            )
        }
        val remove = VersionRetentionPruner.selectForDeletion(
            retained,
            policy,
            System.currentTimeMillis()
        ).map(RetainedVersion::storagePath).toSet()
        files.filter { it.normalizedRelativePath in remove }.forEach { entry ->
            SyncPathResolver.resolve(entry.uri)?.let { runCatching { java.nio.file.Files.deleteIfExists(it) } }
        }
    }
}
