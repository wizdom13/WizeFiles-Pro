package com.wisso.wizefiles.feature.appmanager

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class AppManagerUiState(
    val visibleApps: List<InstalledApp> = emptyList(),
    val selectedPackageNames: Set<String> = emptySet(),
    val query: String = "",
    val filter: AppManagerFilter = AppManagerFilter.USER,
    val sort: AppManagerSort = AppManagerSort.NAME,
    val sortOrder: AppManagerSortOrder = AppManagerSortOrder.ASCENDING,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val totalBackupBytes: Long = 0L
)

class AppManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = InstalledAppRepository(application.packageManager)
    private val mutableState = kotlinx.coroutines.flow.MutableStateFlow(AppManagerUiState())
    val state: kotlinx.coroutines.flow.StateFlow<AppManagerUiState> = mutableState

    private var installedApps: List<InstalledApp> = emptyList()
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        mutableState.value = mutableState.value.copy(isLoading = true, loadFailed = false)
        refreshJob = viewModelScope.launch {
            try {
                val apps = repository.loadInstalledApps()
                installedApps = apps
                val availablePackages = apps.mapTo(mutableSetOf(), InstalledApp::packageName)
                mutableState.value = mutableState.value.copy(
                    selectedPackageNames = mutableState.value.selectedPackageNames
                        .intersect(availablePackages),
                    isLoading = false,
                    loadFailed = false
                )
                rebuildVisibleApps()
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(
                    visibleApps = emptyList(),
                    selectedPackageNames = emptySet(),
                    totalBackupBytes = 0L,
                    isLoading = false,
                    loadFailed = true
                )
            }
        }
    }

    fun setQuery(query: String) {
        if (query == mutableState.value.query) return
        mutableState.value = mutableState.value.copy(
            query = query,
            selectedPackageNames = emptySet()
        )
        rebuildVisibleApps()
    }

    fun setFilter(filter: AppManagerFilter) {
        if (filter == mutableState.value.filter) return
        mutableState.value = mutableState.value.copy(
            filter = filter,
            selectedPackageNames = emptySet()
        )
        rebuildVisibleApps()
    }

    fun setSort(sort: AppManagerSort) {
        if (sort == mutableState.value.sort) return
        mutableState.value = mutableState.value.copy(sort = sort)
        rebuildVisibleApps()
    }

    fun toggleSortOrder() {
        val order = if (mutableState.value.sortOrder == AppManagerSortOrder.ASCENDING) {
            AppManagerSortOrder.DESCENDING
        } else {
            AppManagerSortOrder.ASCENDING
        }
        mutableState.value = mutableState.value.copy(sortOrder = order)
        rebuildVisibleApps()
    }

    fun toggleSelection(packageName: String) {
        val visiblePackages = mutableState.value.visibleApps.mapTo(mutableSetOf(), InstalledApp::packageName)
        if (packageName !in visiblePackages) return
        val selected = mutableState.value.selectedPackageNames.toMutableSet()
        if (!selected.add(packageName)) selected.remove(packageName)
        mutableState.value = mutableState.value.copy(selectedPackageNames = selected)
    }

    fun clearSelection() {
        if (mutableState.value.selectedPackageNames.isNotEmpty()) {
            mutableState.value = mutableState.value.copy(selectedPackageNames = emptySet())
        }
    }

    fun selectedApps(): List<InstalledApp> {
        val state = mutableState.value
        return state.visibleApps.filter { it.packageName in state.selectedPackageNames }
    }

    private fun rebuildVisibleApps() {
        val state = mutableState.value
        val visibleApps = queryInstalledApps(
            apps = installedApps,
            query = state.query,
            filter = state.filter,
            sort = state.sort,
            order = state.sortOrder
        )
        val visiblePackages = visibleApps.mapTo(mutableSetOf(), InstalledApp::packageName)
        mutableState.value = state.copy(
            visibleApps = visibleApps,
            selectedPackageNames = state.selectedPackageNames.intersect(visiblePackages),
            totalBackupBytes = visibleApps.sumOf(InstalledApp::totalApkBytes)
        )
    }
}
