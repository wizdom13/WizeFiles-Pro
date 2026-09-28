// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

import android.content.Context
import com.wisso.wizefiles.core.entitlement.ProFeature
import com.wisso.wizefiles.core.entitlement.ProFeatureAccess
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wisso.wizefiles.feature.transfer.CancelRequestedException
import com.wisso.wizefiles.feature.transfer.OperationControlRegistry
import com.wisso.wizefiles.feature.transfer.PauseRequestedException
import com.wisso.wizefiles.feature.transfer.TransferOperationSpec
import com.wisso.wizefiles.feature.transfer.TransferOperationState
import com.wisso.wizefiles.feature.transfer.TransferOperationType
import com.wisso.wizefiles.feature.transfer.TransferProgressCheckpoint
import com.wisso.wizefiles.feature.transfer.TransferRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex

/** Syncthing owns reconciliation. Never send its destination to the filesystem scan/delete planner. */
internal object SyncthingSyncRunner {
    private val execution = Mutex()

    suspend fun run(
        context: Context,
        profileId: String,
        trigger: SyncRunTrigger,
        resumeRunId: String? = null
    ): SyncWorkerResult = withContext(Dispatchers.IO) {
        if (!execution.tryLock()) return@withContext SyncWorkerResult.RETRY
        var runtime: SyncthingRuntime? = null
        var engine: SyncthingRestEngine? = null
        var run: SyncRun? = null
        var operationId: String? = null
        var scopeAcquired = false
        try {
            if (trigger == SyncRunTrigger.SCHEDULED && !ProFeatureAccess.isAllowed(ProFeature.SCHEDULED_SYNC)) {
                return@withContext SyncWorkerResult.SUCCESS
            }
            val profile = requireNotNull(SyncRepository.profile(profileId))
            val old = resumeRunId?.let(SyncRepository::run)
                ?: SyncRepository.runs(profileId).firstOrNull { !it.state.isTerminal }
            if (old?.state == SyncRunState.PAUSED && resumeRunId == null) {
                return@withContext SyncWorkerResult.SUCCESS
            }
            if (old?.state == SyncRunState.CANCELLED || old?.state == SyncRunState.COMPLETED) {
                return@withContext SyncWorkerResult.SUCCESS
            }
            run = old ?: SyncRepository.createRun(SyncRun(
                profileId = profileId, trigger = trigger, baselineBefore = 0
            ))
            val runId = requireNotNull(run).id
            require(run.profileId == profileId)
            if (run.state == SyncRunState.PLANNING) {
                run = SyncRepository.markRunPreviewReady(runId, SyncPlanSummary())
            }
            if (run.state == SyncRunState.PREVIEW_READY) {
                run = SyncRepository.transitionRun(runId, SyncRunState.APPROVED)
            }
            val prior = run.transferOperationId.takeIf(String::isNotBlank)?.let(TransferRepository::operation)
            val operation = if (prior != null) {
                if (prior.state == TransferOperationState.QUEUED) prior else
                    TransferRepository.transition(prior.id, TransferOperationState.QUEUED)
            } else TransferRepository.enqueue(TransferOperationSpec(
                type = if (profile.mode == SyncMode.TWO_WAY) TransferOperationType.TWO_WAY_SYNC
                    else TransferOperationType.MIRROR,
                sourceUris = listOf(profile.sourceUri), destinationUri = profile.destinationUri
            ))
            operationId = operation.id
            OperationControlRegistry.attach(operation.id)
            SyncRepository.transitionRun(runId, SyncRunState.QUEUED, transferOperationId = operation.id)
            SyncRepository.transitionRun(runId, SyncRunState.RUNNING, transferOperationId = operation.id)
            TransferRepository.transition(operation.id, TransferOperationState.PLANNING)
            ProFeatureAccess.require(ProFeature.SYNC_PROFILES)
            val endpoint = SyncthingProfilePolicy.validate(profile)
            val local = SyncthingLocalFolder.resolve(profile.sourceUri)
            require(!SyncEndpointValidator.scopesOverlap(local.toURI().toString(),
                java.io.File(context.applicationInfo.dataDir).canonicalFile.toURI().toString())) {
                "Select a shared local folder outside app-private storage"
            }
            scopeAcquired = GlobalSyncScopeLocks.registry.tryAcquire(
                runId, listOf(local.toURI().toString(), profile.destinationUri)
            )
            check(scopeAcquired) { "Another sync is using this folder" }
            runtime = SyncthingRuntime.get(context).acquire()
            val publicNetwork = org.json.JSONObject(profile.constraintsJson).optBoolean("syncthingPublicNetwork")
            val options = org.json.JSONObject(runtime.request("GET", "/rest/config/options", null))
                .put("localAnnounceEnabled", true).put("globalAnnounceEnabled", publicNetwork)
                .put("relaysEnabled", publicNetwork).put("natEnabled", publicNetwork)
                .put("listenAddresses", org.json.JSONArray(if (publicNetwork) listOf("default")
                    else listOf("tcp://0.0.0.0:22000", "quic://0.0.0.0:22000")))
            runtime.request("PUT", "/rest/config/options", options.toString())
            engine = SyncthingRestEngine(runtime)
            // Remove engine registrations left by deleted/replaced profiles. This only removes
            // configuration; Syncthing does not delete the local folder on unregister.
            val configured = org.json.JSONArray(runtime.request("GET", "/rest/config/folders", null))
            val profiles = SyncRepository.profiles().filter { SyncBackendRouter.kind(it) == SyncBackendKind.SYNCTHING }
            for (index in 0 until configured.length()) {
                val folder = configured.getJSONObject(index)
                val retained = profiles.any { saved ->
                    SyncthingEndpointCodec.decode(saved.destinationUri)?.folderId == folder.optString("id") &&
                        runCatching { java.io.File(java.net.URI(saved.sourceUri)).canonicalPath ==
                            java.io.File(folder.getString("path")).canonicalPath }.getOrDefault(false)
                }
                if (!retained) runtime.request("DELETE", "/rest/config/folders/" +
                    SyncthingHttp.component(folder.getString("id")), null)
            }
            val filters = SyncFilterCodec.decode(profile.filtersJson)
            require(filters.minimumSizeBytes == 0L && filters.maximumSizeBytes == Long.MAX_VALUE &&
                filters.allowedExtensions.isEmpty() && filters.excludedPathPrefixes.isEmpty() &&
                filters.maximumDepth == Int.MAX_VALUE) { "Unsupported filters for Syncthing" }
            val ignores = buildList {
                // Engine bookkeeping must never be selected as ordinary sync payload.
                add(".wizefiles-versions")
                if (!filters.includeHidden) add(".*")
                filters.excludedExtensions.forEach { extension ->
                    require(extension.matches(Regex("[A-Za-z0-9_-]{1,32}"))) { "Invalid excluded extension" }
                    add("(?i)*.$extension")
                }
            }
            engine.ensureFolder(SyncthingFolderRequest(
                profileId, profile.sourceUri, endpoint, SyncthingProfilePolicy.folderMode(profile), ignores,
                if (org.json.JSONObject(profile.protectionJson).optBoolean("enabled")) 5 else 0
            )).requireSuccess()
            checkControl(operation.id)
            engine.resume(profileId).requireSuccess()
            engine.requestScan(profileId).requireSuccess()
            TransferRepository.transition(operation.id, TransferOperationState.RUNNING)
            val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(8)
            val completion = SyncthingCompletionPolicy()
            while (System.nanoTime() < deadline) {
                checkControl(operation.id)
                val status = engine.folderStatus(profileId)
                check(status.state != SyncthingFolderState.ERROR) { status.error }
                val total = maxOf(status.localBytes, status.remoteBytes, status.pendingBytes)
                TransferRepository.updatePlanSummary(operation.id, status.pendingItems, total)
                TransferRepository.checkpoint(TransferProgressCheckpoint(
                    operation.id, 0, 0, (total - status.pendingBytes).coerceAtLeast(0), status.state.name
                ))
                if (completion.observe(status)) {
                    TransferRepository.transition(operation.id, TransferOperationState.COMPLETED)
                    SyncRepository.transitionRun(runId, SyncRunState.COMPLETED)
                    return@withContext SyncWorkerResult.SUCCESS
                }
                delay(2_000)
            }
            // A disconnected peer or unfinished transfer is never reported as a successful backup.
            throw java.io.IOException("Peer unavailable or synchronization still pending; retry to continue")
        } catch (_: PauseRequestedException) {
            finish(run, operationId, SyncRunState.PAUSED, TransferOperationState.PAUSED)
            SyncWorkerResult.SUCCESS
        } catch (_: CancelRequestedException) {
            finish(run, operationId, SyncRunState.CANCELLED, TransferOperationState.CANCELLED)
            SyncWorkerResult.SUCCESS
        } catch (cancelled: CancellationException) {
            finish(run, operationId, SyncRunState.PAUSED, TransferOperationState.RECOVERABLE)
            throw cancelled
        } catch (failure: Exception) {
            finish(run, operationId, SyncRunState.FAILED, TransferOperationState.FAILED,
                failure.message.orEmpty().take(320))
            if (trigger == SyncRunTrigger.MANUAL) SyncWorkerResult.FAILURE else SyncWorkerResult.RETRY
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                try {
                    runCatching { engine?.pause(profileId)?.requireSuccess() }
                    try { runtime?.abort() } finally { runtime?.release() }
                } finally {
                    runCatching {
                        run?.let { completed ->
                            val state = SyncRepository.run(completed.id)?.state
                            if (state == SyncRunState.COMPLETED || state == SyncRunState.FAILED) {
                                SyncRepository.profile(profileId)?.let { saved ->
                                    SyncRepository.saveProfile(saved.copy(
                                        lastRunAtMillis = System.currentTimeMillis(),
                                        consecutiveFailures = if (state == SyncRunState.FAILED)
                                            saved.consecutiveFailures + 1 else 0))
                                }
                            }
                        }
                    }
                    OperationControlRegistry.detach(operationId)
                    if (scopeAcquired) run?.let { GlobalSyncScopeLocks.registry.release(it.id) }
                    execution.unlock()
                }
            }
        }
    }

    private fun checkControl(id: String) {
        OperationControlRegistry.throwIfPauseRequested(id)
        when (TransferRepository.operation(id)?.state) {
            TransferOperationState.PAUSED, TransferOperationState.PAUSE_REQUESTED -> throw PauseRequestedException()
            TransferOperationState.CANCELLED -> throw CancelRequestedException()
            else -> Unit
        }
    }

    private fun finish(run: SyncRun?, operationId: String?, state: SyncRunState,
        transferState: TransferOperationState, message: String = "") {
        operationId?.let { runCatching {
            if (transferState == TransferOperationState.PAUSED &&
                TransferRepository.operation(it)?.state in setOf(TransferOperationState.PLANNING,
                    TransferOperationState.RUNNING)) {
                TransferRepository.transition(it, TransferOperationState.PAUSE_REQUESTED)
            }
            TransferRepository.transition(it, transferState,
                errorCategory = if (message.isNotBlank()) "SYNCTHING" else "", errorMessage = message)
        } }
        run?.let { runCatching { SyncRepository.transitionRun(it.id, state) } }
    }
}

/** Require stable local AND peer completion after a scan, not just a momentary local idle state. */
internal class SyncthingCompletionPolicy {
    private var idleSamples = 0
    fun observe(status: SyncthingFolderStatus): Boolean {
        idleSamples = if (status.state == SyncthingFolderState.IDLE &&
            status.pendingBytes == 0L && status.pendingItems == 0L && status.error.isEmpty()) idleSamples + 1 else 0
        return idleSamples >= 3
    }
}

internal class SyncthingRunWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("profile") ?: return Result.failure()
        tryEnterSyncForeground(id)
        SyncRecoveryManager.reconcileDetachedRun(id)
        return when (SyncthingSyncRunner.run(applicationContext, id, SyncRunTrigger.MANUAL,
            inputData.getString("run"))) {
            SyncWorkerResult.SUCCESS -> Result.success()
            // Busy runtime gets queued again; a failed manual run remains visible in history.
            SyncWorkerResult.RETRY -> Result.retry()
            SyncWorkerResult.FAILURE -> Result.failure()
        }
    }

    companion object {
        fun enqueue(context: Context, profileId: String, runId: String? = null) {
            WorkManager.getInstance(context).enqueueUniqueWork("syncthing-manual-$profileId",
                ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<SyncthingRunWorker>()
                    .setInputData(Data.Builder().putString("profile", profileId).putString("run", runId).build())
                    .build())
        }
    }
}
