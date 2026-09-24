package com.wisso.wizefiles.storagecleaner

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StorageCleanerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val application = Application()
    private val stringResolver = object : StorageCleanerStringResolver {
        override fun getString(resId: Int, vararg formatArgs: Any): String = when (resId) {
            com.wisso.wizefiles.R.string.transfer_section_failed -> "Failed"
            com.wisso.wizefiles.R.string.storage_cleaner_scan_failed -> "Storage scan failed"
            com.wisso.wizefiles.R.string.storage_cleaner_permission_hint_usage_access ->
                "Grant Usage Access for Unused Apps analysis"
            com.wisso.wizefiles.R.string.storage_cleaner_no_recommendations ->
                "No cleanup recommendations found"
            com.wisso.wizefiles.R.string.storage_cleaner_limited_results ->
                "Limited results: ${formatArgs.firstOrNull()?.toString().orEmpty()}"
            else -> "resource-$resId"
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun scanFailureDoesNotCrashAndSetsErrorMessage() = runTest {
        val repository = object : StorageAnalysisDataSource {
            override suspend fun analyze(
                filters: ScanFilters,
                ignoredIds: Set<String>
            ): StorageAnalysisResult {
                error("boom")
            }
        }
        val viewModel = StorageCleanerViewModel(
            application,
            repository,
            FakeCacheStore(),
            nowMillisProvider = { 123L },
            preferenceStore = FakePreferenceStore(),
            stringResolver = stringResolver
        )
        viewModel.refreshScan()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isScanning)
        assertEquals("Storage scan failed", state.message)
        assertEquals(0, state.analysis?.recommendations?.size)
    }

    @Test
    fun onScreenOpenedLoadsCachedAnalysisWithoutAutoRescan() = runTest {
        val cachedAnalysis = sampleAnalysis("cached-id")
        val cacheStore = FakeCacheStore(
            cached = CachedStorageAnalysis(cachedAnalysis, 77L, ScanFilters(oldFileDays = 90))
        )
        val repository = RecordingRepository(sampleAnalysis("fresh-id"))
        val viewModel = StorageCleanerViewModel(
            application,
            repository,
            cacheStore,
            preferenceStore = FakePreferenceStore(),
            stringResolver = stringResolver
        )

        viewModel.onScreenOpened()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, repository.calls)
        assertEquals(cachedAnalysis, state.analysis)
        assertEquals(77L, state.lastUpdatedMillis)
        assertTrue(state.selectedIds.isEmpty())
    }

    @Test
    fun refreshScanReplacesCacheAndLeavesRecommendationsDeselected() = runTest {
        val fresh = sampleAnalysis("fresh-id")
        val cacheStore = FakeCacheStore()
        val repository = RecordingRepository(fresh)
        val viewModel = StorageCleanerViewModel(
            application,
            repository,
            cacheStore,
            nowMillisProvider = { 999L },
            preferenceStore = FakePreferenceStore(),
            stringResolver = stringResolver
        )

        viewModel.refreshScan()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, repository.calls)
        assertEquals(fresh, state.analysis)
        assertTrue(state.selectedIds.isEmpty())
        assertEquals(999L, state.lastUpdatedMillis)
        assertNotNull(cacheStore.saved)
        assertEquals(fresh, cacheStore.saved?.analysis)
        assertEquals(999L, cacheStore.saved?.lastUpdatedMillis)
    }

    @Test
    fun permissionGrantOnlyRescansWhenAnalysisNeedsUsageAccess() = runTest {
        val repository = RecordingRepository(
            sampleAnalysis(
                "with-unused-cap",
                missingCapabilities = listOf("Grant Usage Access for Unused Apps analysis")
            )
        )
        val viewModel = StorageCleanerViewModel(
            application,
            repository,
            FakeCacheStore(),
            preferenceStore = FakePreferenceStore(),
            stringResolver = stringResolver
        )

        viewModel.onUsageAccessPermissionChanged(permissionGranted = true)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repository.calls)

        repository.calls = 0
        repository.nextResult = sampleAnalysis("no-missing", missingCapabilities = emptyList())
        viewModel.refreshScan()
        dispatcher.scheduler.advanceUntilIdle()

        repository.calls = 0
        viewModel.onUsageAccessPermissionChanged(permissionGranted = true)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, repository.calls)
    }

    @Test
    fun refreshScanIgnoresDuplicateRequestsWhileScanIsRunning() = runTest {
        val repository = object : StorageAnalysisDataSource {
            var calls = 0
            override suspend fun analyze(
                filters: ScanFilters,
                ignoredIds: Set<String>
            ): StorageAnalysisResult {
                calls += 1
                delay(1_000)
                return sampleAnalysis("single-run")
            }
        }
        val viewModel = StorageCleanerViewModel(
            application,
            repository,
            FakeCacheStore(),
            preferenceStore = FakePreferenceStore(),
            stringResolver = stringResolver
        )

        viewModel.refreshScan()
        dispatcher.scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isScanning)

        viewModel.refreshScan()
        viewModel.refreshScan()
        dispatcher.scheduler.advanceTimeBy(999)
        assertTrue(viewModel.uiState.value.isScanning)

        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repository.calls)
        assertFalse(viewModel.uiState.value.isScanning)
    }

    @Test
    fun ignoreRecommendationPersistsAndRemovesSelection() = runTest {
        val preferenceStore = FakePreferenceStore()
        val viewModel = StorageCleanerViewModel(
            application,
            RecordingRepository(sampleAnalysis("large:item")),
            FakeCacheStore(),
            nowMillisProvider = { 42L },
            preferenceStore = preferenceStore,
            stringResolver = stringResolver
        )
        viewModel.refreshScan()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleSelection("large:item", true)

        val ignored = viewModel.ignoreRecommendation("large:item")

        assertNotNull(ignored)
        assertEquals(42L, ignored?.ignoredAtMillis)
        assertTrue("large:item" in viewModel.uiState.value.ignoredIds)
        assertFalse("large:item" in viewModel.uiState.value.selectedIds)
        assertEquals(listOf("large:item"), preferenceStore.current.ignoredItems.map { it.id })
    }

    @Test
    fun ignoredRecommendationCanBeRestoredWithoutRescan() = runTest {
        val preferenceStore = FakePreferenceStore()
        val viewModel = StorageCleanerViewModel(
            application,
            RecordingRepository(sampleAnalysis("large:item")),
            FakeCacheStore(),
            preferenceStore = preferenceStore,
            stringResolver = stringResolver
        )
        viewModel.refreshScan()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.ignoreRecommendation("large:item")

        viewModel.restoreIgnored("large:item")

        assertTrue(viewModel.uiState.value.ignoredIds.isEmpty())
        assertTrue(viewModel.uiState.value.analysis?.recommendations?.any {
            it.id == "large:item"
        } == true)
        assertTrue(preferenceStore.current.ignoredItems.isEmpty())
    }

    @Test
    fun duplicateKeepOverridePersistsAndCanResetToRecommendation() = runTest {
        val analysis = duplicateAnalysis()
        val preferenceStore = FakePreferenceStore()
        val cacheStore = FakeCacheStore(CachedStorageAnalysis(analysis, 5L, ScanFilters()))
        val viewModel = StorageCleanerViewModel(
            application,
            RecordingRepository(analysis),
            cacheStore,
            preferenceStore = preferenceStore,
            stringResolver = stringResolver
        )
        viewModel.onScreenOpened()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.setDuplicateKeepCandidate("dup:group:members", "/storage/emulated/0/b.jpg"))
        var group = viewModel.uiState.value.analysis
            ?.recommendations?.single()?.duplicateGroup
        assertEquals("/storage/emulated/0/b.jpg", group?.keepCandidatePath)
        assertEquals("/storage/emulated/0/b.jpg", preferenceStore.current.duplicateKeepOverrides["group"])
        assertEquals(10L, viewModel.uiState.value.analysis?.recommendations?.single()?.reclaimableBytes)

        assertTrue(viewModel.setDuplicateKeepCandidate("dup:group:members", "/storage/emulated/0/a.jpg"))
        group = viewModel.uiState.value.analysis?.recommendations?.single()?.duplicateGroup
        assertEquals("/storage/emulated/0/a.jpg", group?.keepCandidatePath)
        assertTrue(preferenceStore.current.duplicateKeepOverrides.isEmpty())
        assertNotNull(cacheStore.saved)
    }

    @Test
    fun staleDuplicateKeepOverrideRequiresExplicitReview() = runTest {
        val analysis = duplicateAnalysis()
        val preferenceStore = FakePreferenceStore(
            StorageCleanerPreferences(
                duplicateKeepOverrides = mapOf("group" to "/storage/emulated/0/missing.jpg")
            )
        )
        val viewModel = StorageCleanerViewModel(
            application,
            RecordingRepository(analysis),
            FakeCacheStore(CachedStorageAnalysis(analysis, 5L, ScanFilters())),
            preferenceStore = preferenceStore,
            stringResolver = stringResolver
        )

        viewModel.onScreenOpened()
        dispatcher.scheduler.advanceUntilIdle()

        val group = viewModel.uiState.value.analysis
            ?.recommendations?.single()?.duplicateGroup
        assertEquals("/storage/emulated/0/a.jpg", group?.keepCandidatePath)
        assertTrue(group?.keepSelectionRequiresReview == true)
        assertEquals(
            "/storage/emulated/0/missing.jpg",
            preferenceStore.current.duplicateKeepOverrides["group"]
        )

        viewModel.toggleSelection("dup:group:members", true)
        assertTrue(viewModel.uiState.value.selectedIds.isEmpty())

        assertTrue(
            viewModel.setDuplicateKeepCandidate(
                "dup:group:members",
                "/storage/emulated/0/a.jpg"
            )
        )
        val confirmedGroup = viewModel.uiState.value.analysis
            ?.recommendations?.single()?.duplicateGroup
        assertFalse(confirmedGroup?.keepSelectionRequiresReview == true)
        assertTrue(preferenceStore.current.duplicateKeepOverrides.isEmpty())
    }

    @Test
    fun unusedAppsCannotEnterFileDeletionSelection() = runTest {
        val unusedId = "app:com.example.unused"
        val analysis = sampleAnalysis("file-id").copy(
            recommendations = listOf(
                CleanupRecommendation(
                    id = unusedId,
                    type = RecommendationType.UNUSED_APP,
                    title = "Unused App",
                    reason = "Not used recently",
                    reclaimableBytes = 100L,
                    packageName = "com.example.unused",
                    score = RecommendationScore(100L, 0.85, 0.7, 0.9, 0.0, false),
                    preselected = false
                )
            )
        )
        val viewModel = StorageCleanerViewModel(
            application,
            RecordingRepository(analysis),
            FakeCacheStore(),
            preferenceStore = FakePreferenceStore(),
            stringResolver = stringResolver
        )
        viewModel.refreshScan()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(unusedId, true)
        viewModel.setSectionSelection(listOf(unusedId), true)

        assertTrue(viewModel.uiState.value.selectedIds.isEmpty())
    }

    @Test
    fun executeDeletionQueuesValidatedTargetsAndClearsQueuedSelection() = runTest {
        val recommendationId = "large:item"
        val previewItem = DeletePreviewItem(
            recommendationId = recommendationId,
            path = "/storage/emulated/0/demo.bin",
            displayName = "demo.bin",
            bytes = 200L,
            reason = "Old backup"
        )
        val planner = object : StorageCleanupDeletionPlanner {
            override fun buildPreview(
                selected: List<CleanupRecommendation>
            ): List<DeletePreviewItem> {
                assertEquals(listOf(recommendationId), selected.map { it.id })
                return listOf(previewItem)
            }

            override fun validateTargets(
                items: List<DeletePreviewItem>
            ): List<DeletePreviewItem> = items
        }
        var queuedPaths = emptyList<String>()
        val queue = StorageCleanupDeletionQueue { paths, _ ->
            queuedPaths = paths.map { it.toString() }
            "cleanup-operation"
        }
        val viewModel = StorageCleanerViewModel(
            application,
            RecordingRepository(sampleAnalysis(recommendationId)),
            FakeCacheStore(),
            preferenceStore = FakePreferenceStore(),
            deletionPlanner = planner,
            deletionQueue = queue,
            stringResolver = stringResolver
        )
        viewModel.refreshScan()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleSelection(recommendationId, true)

        val operationId = viewModel.executeDeletion()

        assertEquals("cleanup-operation", operationId)
        assertEquals(listOf(previewItem.path), queuedPaths)
        assertTrue(viewModel.uiState.value.selectedIds.isEmpty())
        assertFalse(viewModel.uiState.value.isPreparingDeletion)
    }

    @Test
    fun executeDeletionKeepsSelectionWhenNothingPassesValidation() = runTest {
        val recommendationId = "large:item"
        val planner = object : StorageCleanupDeletionPlanner {
            override fun buildPreview(
                selected: List<CleanupRecommendation>
            ): List<DeletePreviewItem> = listOf(
                DeletePreviewItem(
                    recommendationId,
                    "/storage/emulated/0/demo.bin",
                    "demo.bin",
                    200L,
                    "Old backup"
                )
            )

            override fun validateTargets(
                items: List<DeletePreviewItem>
            ): List<DeletePreviewItem> = emptyList()
        }
        var queueCalled = false
        val viewModel = StorageCleanerViewModel(
            application,
            RecordingRepository(sampleAnalysis(recommendationId)),
            FakeCacheStore(),
            preferenceStore = FakePreferenceStore(),
            deletionPlanner = planner,
            deletionQueue = StorageCleanupDeletionQueue { _, _ ->
                queueCalled = true
                "unexpected"
            },
            stringResolver = stringResolver
        )
        viewModel.refreshScan()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleSelection(recommendationId, true)

        val operationId = viewModel.executeDeletion()

        assertEquals(null, operationId)
        assertFalse(queueCalled)
        assertEquals(setOf(recommendationId), viewModel.uiState.value.selectedIds)
        assertFalse(viewModel.uiState.value.isPreparingDeletion)
    }

    private class RecordingRepository(var nextResult: StorageAnalysisResult) :
        StorageAnalysisDataSource {
        var calls: Int = 0

        override suspend fun analyze(
            filters: ScanFilters,
            ignoredIds: Set<String>
        ): StorageAnalysisResult {
            calls += 1
            return nextResult
        }
    }

    private class FakeCacheStore(
        private val cached: CachedStorageAnalysis? = null
    ) : StorageCleanerCacheStoreApi {
        var saved: CachedStorageAnalysis? = null

        override fun load(): CachedStorageAnalysis? = cached

        override fun save(
            analysis: StorageAnalysisResult,
            filters: ScanFilters,
            lastUpdatedMillis: Long
        ) {
            saved = CachedStorageAnalysis(analysis, lastUpdatedMillis, filters)
        }

        override fun clear() = Unit
    }

    private class FakePreferenceStore(
        initial: StorageCleanerPreferences = StorageCleanerPreferences()
    ) : StorageCleanerPreferenceStoreApi {
        var current = initial
            private set

        override fun load(): StorageCleanerPreferences = current

        override fun save(preferences: StorageCleanerPreferences) {
            current = preferences
        }

        override fun clear() {
            current = StorageCleanerPreferences()
        }
    }

    private fun sampleAnalysis(
        id: String,
        missingCapabilities: List<String> = emptyList()
    ): StorageAnalysisResult {
        return StorageAnalysisResult(
            compositionCategories = listOf(
                StorageCompositionSummary(StorageCompositionCategory.DOCUMENTS, 200L, 1)
            ),
            recommendations = listOf(
                CleanupRecommendation(
                    id = id,
                    type = RecommendationType.LARGE_FILE,
                    title = "Large File",
                    reason = "Old backup",
                    reclaimableBytes = 200L,
                    path = "/tmp/demo",
                    score = RecommendationScore(200L, 1.0, 0.8, 0.5, 0.0, false),
                    preselected = true
                )
            ),
            progress = ScanProgress(10, 10, "Completed", true),
            missingCapabilities = missingCapabilities
        )
    }

    private fun duplicateAnalysis(): StorageAnalysisResult {
        val group = DuplicateGroup(
            id = "group",
            hash = "hash",
            candidates = listOf(
                FileCandidate("/storage/emulated/0/a.jpg", 10L, 2L),
                FileCandidate("/storage/emulated/0/b.jpg", 20L, 1L)
            ),
            keepCandidatePath = "/storage/emulated/0/a.jpg",
            recommendedKeepCandidatePath = "/storage/emulated/0/a.jpg"
        )
        return StorageAnalysisResult(
            compositionCategories = emptyList(),
            recommendations = listOf(
                CleanupRecommendation(
                    id = "dup:group:members",
                    type = RecommendationType.DUPLICATE_MEDIA,
                    title = "Duplicate media",
                    reason = "Duplicate group",
                    reclaimableBytes = 20L,
                    duplicateGroup = group,
                    score = RecommendationScore(20L, 0.99, 0.9, 0.5, 1.0, false),
                    preselected = true
                )
            ),
            progress = ScanProgress(2, 2, "Completed", true),
            missingCapabilities = emptyList()
        )
    }
}
