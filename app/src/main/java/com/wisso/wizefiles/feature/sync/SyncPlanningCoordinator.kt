package com.wisso.wizefiles.feature.sync

internal class SyncPlanningCoordinator(
    private val repository: SyncPlanRepository = DatabaseSyncPlanRepository
) {
    fun planFirstOrManualRun(
        profile: SyncProfile,
        trigger: SyncRunTrigger,
        sourceEntries: Sequence<SyncFileEntry>,
        destinationEntries: Sequence<SyncFileEntry>,
        filters: SyncFilterRules = SyncFilterRules(),
        caseSensitive: Boolean = true,
        scanErrors: List<String> = emptyList()
    ): Pair<SyncRun, SyncPlan> {
        SyncDomainPolicy.validate(profile)
        require(profile.mode == SyncMode.UPDATE_DESTINATION) {
            "Stage 2 supports update-destination planning only"
        }
        val run = repository.createRun(
            SyncRun(
                profileId = profile.id,
                trigger = trigger,
                baselineBefore = profile.baselineGeneration
            )
        )
        val plan = UpdateDestinationPlanner(
            comparisonPolicy = profile.comparisonPolicy,
            filters = filters,
            caseSensitive = caseSensitive
        ).plan(
            runId = run.id,
            sourceEntries = sourceEntries,
            destinationEntries = destinationEntries,
            destinationRootUri = profile.destinationUri,
            scanErrors = scanErrors
        )
        repository.savePlan(run.id, plan)
        val preview = repository.markPreviewReady(run.id, plan.summary)
        return preview to plan
    }
}

internal interface SyncPlanRepository {
    fun createRun(run: SyncRun): SyncRun
    fun savePlan(runId: String, plan: SyncPlan)
    fun markPreviewReady(runId: String, summary: SyncPlanSummary): SyncRun
}

internal object DatabaseSyncPlanRepository : SyncPlanRepository {
    override fun createRun(run: SyncRun): SyncRun = SyncRepository.createRun(run)

    override fun savePlan(runId: String, plan: SyncPlan) {
        SyncRepository.replaceActions(runId, plan.actions)
    }

    override fun markPreviewReady(runId: String, summary: SyncPlanSummary): SyncRun =
        SyncRepository.markRunPreviewReady(runId, summary)
}
