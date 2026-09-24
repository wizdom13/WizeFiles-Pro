package com.wisso.wizefiles.storagecleaner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.nio.file.Paths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface StorageCleanerStringResolver {
    fun getString(resId: Int, vararg formatArgs: Any): String
}

private class AndroidStorageCleanerStringResolver(
    private val application: Application
) : StorageCleanerStringResolver {
    override fun getString(resId: Int, vararg formatArgs: Any): String =
        application.getString(resId, *formatArgs)
}

data class StorageCleanerUiState(
    val isScanning: Boolean = false,
    val analysis: StorageAnalysisResult? = null,
    val selectedIds: Set<String> = emptySet(),
    val ignoredIds: Set<String> = emptySet(),
    val ignoredItems: List<IgnoredCleanupItem> = emptyList(),
    val isPreparingDeletion: Boolean = false,
    val message: String? = null,
    val lastUpdatedMillis: Long? = null,
    val lastScanFilters: ScanFilters = ScanFilters()
)

class StorageCleanerViewModel(
    application: Application,
    private val repository: StorageAnalysisDataSource = StorageAnalysisRepository(application),
    private val cacheStore: StorageCleanerCacheStoreApi = StorageCleanerCacheStore(application),
    private val nowMillisProvider: () -> Long = { System.currentTimeMillis() },
    private val preferenceStore: StorageCleanerPreferenceStoreApi =
        StorageCleanerPreferenceStore(application),
    private val deletionPlanner: StorageCleanupDeletionPlanner = DeletionPlanner(),
    private val deletionQueue: StorageCleanupDeletionQueue =
        FileOperationStorageCleanupDeletionQueue,
    private val stringResolver: StorageCleanerStringResolver =
        AndroidStorageCleanerStringResolver(application)
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        repository = StorageAnalysisRepository(application),
        cacheStore = StorageCleanerCacheStore(application),
        nowMillisProvider = { System.currentTimeMillis() },
        preferenceStore = StorageCleanerPreferenceStore(application)
    )

    private var preferences = preferenceStore.load()
    private val _uiState = MutableStateFlow(
        StorageCleanerUiState(
            ignoredIds = preferences.ignoredItems.mapTo(mutableSetOf()) { it.id },
            ignoredItems = preferences.ignoredItems
        )
    )
    val uiState: StateFlow<StorageCleanerUiState> = _uiState
    private var didLoadInitialState = false

    fun onScreenOpened() {
        if (didLoadInitialState) return
        didLoadInitialState = true

        val cached = cacheStore.load()
        if (cached != null) {
            val analysis = applyKeepPreferences(cached.analysis)
            _uiState.update {
                it.copy(
                    analysis = analysis,
                    selectedIds = emptySet(),
                    message = buildAnalysisMessage(analysis, it.ignoredIds),
                    lastUpdatedMillis = cached.lastUpdatedMillis,
                    lastScanFilters = cached.filters
                )
            }
            return
        }
        refreshScan()
    }

    fun refreshScan(filters: ScanFilters = _uiState.value.lastScanFilters) {
        if (_uiState.value.isScanning) return
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, message = null, lastScanFilters = filters) }
            try {
                val analyzed = repository.analyze(filters, _uiState.value.ignoredIds)
                val analysis = applyKeepPreferences(analyzed)
                val now = nowMillisProvider()
                cacheStore.save(analysis, filters, now)
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        analysis = analysis,
                        message = buildAnalysisMessage(analysis, it.ignoredIds),
                        selectedIds = emptySet(),
                        lastUpdatedMillis = now,
                        lastScanFilters = filters
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        analysis = it.analysis
                            ?: StorageAnalysisResult(
                                compositionCategories = emptyList(),
                                recommendations = emptyList(),
                                progress = ScanProgress(
                                    0,
                                    0,
                                    stringResolver.getString(
                                        com.wisso.wizefiles.R.string.transfer_section_failed
                                    ),
                                    true
                                ),
                                missingCapabilities = emptyList()
                            ),
                        selectedIds = emptySet(),
                        message = stringResolver.getString(
                            com.wisso.wizefiles.R.string.storage_cleaner_scan_failed
                        )
                    )
                }
            }
        }
    }

    fun onUsageAccessPermissionChanged(permissionGranted: Boolean) {
        if (!permissionGranted) return
        val currentAnalysis = _uiState.value.analysis
        val shouldRescan = currentAnalysis == null || currentAnalysis.requiresUsageAccessForFullResults()
        if (shouldRescan) refreshScan()
    }

    fun toggleSelection(id: String, selected: Boolean) {
        _uiState.update { state ->
            val recommendation = state.analysis?.recommendations
                ?.firstOrNull { it.id == id }
            val canSelect = recommendation?.isDeletionCandidate == true &&
                id !in state.ignoredIds
            val updated = state.selectedIds.toMutableSet()
            if (selected && canSelect) updated += id else updated -= id
            state.copy(selectedIds = updated)
        }
    }

    fun setSectionSelection(recommendationIds: Collection<String>, selected: Boolean) {
        if (recommendationIds.isEmpty()) return
        _uiState.update { state ->
            val requestedIds = recommendationIds.toSet()
            val eligible = state.analysis?.recommendations
                .orEmpty()
                .filter {
                    it.id in requestedIds &&
                        it.id !in state.ignoredIds &&
                        it.isDeletionCandidate
                }
                .mapTo(mutableSetOf()) { it.id }
            val updated = state.selectedIds.toMutableSet()
            if (selected) updated += eligible else updated -= requestedIds
            state.copy(selectedIds = updated)
        }
    }

    fun ignoreRecommendation(id: String): IgnoredCleanupItem? {
        val recommendation = _uiState.value.analysis?.recommendations?.firstOrNull { it.id == id }
            ?: return null
        val ignored = IgnoredCleanupItem(
            id = recommendation.id,
            type = recommendation.type,
            title = recommendation.title,
            location = recommendation.path
                ?: recommendation.packageName
                ?: recommendation.duplicateGroup?.keepCandidatePath,
            ignoredAtMillis = nowMillisProvider()
        )
        preferences = preferences.copy(
            ignoredItems = (preferences.ignoredItems.filterNot { it.id == ignored.id } + ignored)
                .sortedByDescending { it.ignoredAtMillis }
        )
        savePreferences()
        _uiState.update {
            it.copy(
                selectedIds = it.selectedIds - id,
                ignoredIds = preferences.ignoredItems.mapTo(mutableSetOf()) { item -> item.id },
                ignoredItems = preferences.ignoredItems
            )
        }
        return ignored
    }

    fun restoreIgnored(id: String) {
        preferences = preferences.copy(
            ignoredItems = preferences.ignoredItems.filterNot { it.id == id }
        )
        savePreferences()
        _uiState.update {
            it.copy(
                ignoredIds = preferences.ignoredItems.mapTo(mutableSetOf()) { item -> item.id },
                ignoredItems = preferences.ignoredItems
            )
        }
    }

    fun restoreAllIgnored() {
        preferences = preferences.copy(ignoredItems = emptyList())
        savePreferences()
        _uiState.update { it.copy(ignoredIds = emptySet(), ignoredItems = emptyList()) }
    }

    fun setDuplicateKeepCandidate(recommendationId: String, candidatePath: String): Boolean {
        val recommendation = _uiState.value.analysis?.recommendations
            ?.firstOrNull { it.id == recommendationId } ?: return false
        val group = recommendation.duplicateGroup ?: return false
        if (group.candidates.none { it.path == candidatePath }) return false

        val overrides = preferences.duplicateKeepOverrides.toMutableMap()
        if (candidatePath == group.recommendedKeepCandidatePath) {
            overrides.remove(group.id)
        } else {
            overrides[group.id] = candidatePath
        }
        preferences = preferences.copy(duplicateKeepOverrides = overrides)
        savePreferences()
        val updatedAnalysis = applyKeepPreferences(requireNotNull(_uiState.value.analysis))
        _uiState.update { it.copy(analysis = updatedAnalysis) }
        saveCurrentAnalysis(updatedAnalysis)
        return true
    }

    suspend fun executeDeletion(): String? {
        if (_uiState.value.isPreparingDeletion) return null
        _uiState.update { it.copy(isPreparingDeletion = true) }
        return try {
            val valid = validateSelectedTargets()
            if (valid.isEmpty()) return null
            val operationId = deletionQueue.enqueue(
                valid.map { Paths.get(it.path) },
                getApplication()
            )
            if (operationId != null) {
                val queuedRecommendationIds = valid.mapTo(mutableSetOf()) {
                    it.recommendationId
                }
                _uiState.update {
                    it.copy(selectedIds = it.selectedIds - queuedRecommendationIds)
                }
            }
            operationId
        } finally {
            _uiState.update { it.copy(isPreparingDeletion = false) }
        }
    }

    suspend fun buildDeletePreview(): List<DeletePreviewItem> {
        if (_uiState.value.isPreparingDeletion) return emptyList()
        _uiState.update { it.copy(isPreparingDeletion = true) }
        return try {
            validateSelectedTargets()
        } finally {
            _uiState.update { it.copy(isPreparingDeletion = false) }
        }
    }

    private suspend fun validateSelectedTargets(): List<DeletePreviewItem> {
        val state = _uiState.value
        val selected = state.analysis?.recommendations
            ?.filter { it.id in state.selectedIds && it.id !in state.ignoredIds }
            .orEmpty()
        return withContext(Dispatchers.IO) {
            deletionPlanner.validateTargets(deletionPlanner.buildPreview(selected))
        }
    }

    private fun applyKeepPreferences(analysis: StorageAnalysisResult): StorageAnalysisResult {
        val overrides = preferences.duplicateKeepOverrides
        val recommendations = analysis.recommendations.map { recommendation ->
            val group = recommendation.duplicateGroup ?: return@map recommendation
            val preferredPath = overrides[group.id]
            val preferredPathIsAvailable = preferredPath == null ||
                group.candidates.any { it.path == preferredPath }
            val effectivePath = preferredPath
                ?.takeIf { preferredPathIsAvailable }
                ?: group.recommendedKeepCandidatePath
            val updatedGroup = group.copy(
                keepCandidatePath = effectivePath,
                keepSelectionRequiresReview =
                    preferredPath != null && !preferredPathIsAvailable
            )
            val reclaimable = updatedGroup.candidates
                .filterNot { it.path == effectivePath }
                .sumOf { it.size }
            recommendation.copy(
                duplicateGroup = updatedGroup,
                reclaimableBytes = reclaimable,
                score = recommendation.score.copy(reclaimableBytes = reclaimable)
            )
        }
        return analysis.copy(recommendations = recommendations)
    }

    private fun saveCurrentAnalysis(analysis: StorageAnalysisResult) {
        val state = _uiState.value
        val updatedAt = state.lastUpdatedMillis ?: nowMillisProvider()
        cacheStore.save(analysis, state.lastScanFilters, updatedAt)
    }

    private fun savePreferences() {
        preferenceStore.save(preferences)
    }

    private fun StorageAnalysisResult.requiresUsageAccessForFullResults(): Boolean {
        val usageAccessMessage = stringResolver.getString(
            com.wisso.wizefiles.R.string.storage_cleaner_permission_hint_usage_access
        )
        return missingCapabilities.any { it == usageAccessMessage }
    }

    private fun buildAnalysisMessage(
        analysis: StorageAnalysisResult,
        ignoredIds: Set<String>
    ): String? = when {
        analysis.recommendations.none { it.id !in ignoredIds } &&
            analysis.missingCapabilities.isEmpty() ->
            stringResolver.getString(
                com.wisso.wizefiles.R.string.storage_cleaner_no_recommendations
            )
        analysis.missingCapabilities.isNotEmpty() ->
            stringResolver.getString(
                com.wisso.wizefiles.R.string.storage_cleaner_limited_results,
                analysis.missingCapabilities.first()
            )
        else -> null
    }
}
