// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import android.os.Parcelable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.map
import java.nio.file.Path
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.feature.filebrowser.FileSortOptions.By
import com.wisso.wizefiles.feature.filebrowser.FileSortOptions.Order
import com.wisso.wizefiles.provider.archive.archiveRefresh
import com.wisso.wizefiles.provider.archive.isArchivePath
import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.util.Loading
import com.wisso.wizefiles.util.CloseableLiveData
import com.wisso.wizefiles.util.Stateful
import com.wisso.wizefiles.util.valueCompat
import java.io.Closeable

class FileListViewModel(private val savedStateHandle: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    init {
        BrowserProcessRestorationPolicy.sanitize(savedStateHandle)
    }

    private val savedSearch = BrowserSearchSavedState(savedStateHandle)
    private val trailLiveData = TrailLiveData()
    val hasTrail: Boolean
        get() = trailLiveData.value != null
    val pendingState: Parcelable?
        get() = trailLiveData.valueCompat.pendingState

    fun navigateTo(lastState: Parcelable, path: Path) {
        BrowserSessionState.recordDirectory(path)
        trailLiveData.navigateTo(lastState, path)
    }

    fun resetTo(path: Path) {
        BrowserSessionState.recordDirectory(path)
        trailLiveData.resetTo(path)
    }

    fun navigateUp(): Boolean {
        val navigated = trailLiveData.navigateUp()
        if (navigated) {
            BrowserSessionState.recordDirectory(trailLiveData.valueCompat.currentPath)
        }
        return navigated
    }

    val currentPathLiveData = trailLiveData.map { it.currentPath }
    val currentPath: Path
        get() = currentPathLiveData.valueCompat

    private val _searchStateLiveData = MutableLiveData(
        SearchState(
            savedSearch.isSearching,
            savedSearch.query
        )
    )
    val searchStateLiveData: LiveData<SearchState> = _searchStateLiveData
    val searchState: SearchState
        get() = _searchStateLiveData.valueCompat

    fun search(query: String) {
        val searchState = _searchStateLiveData.valueCompat
        if (searchState.isSearching && searchState.query == query) {
            return
        }
        _searchStateLiveData.value = SearchState(true, query)
        savedSearch.isSearching = true
        savedSearch.query = query
    }

    fun stopSearching() {
        val searchState = _searchStateLiveData.valueCompat
        if (!searchState.isSearching) {
            return
        }
        _searchStateLiveData.value = SearchState(false, "")
        savedSearch.stop()
    }

    private val _fileListLiveData =
        FileListSwitchMapLiveData(currentPathLiveData, _searchStateLiveData)
    val fileListLiveData: LiveData<Stateful<List<FileItem>>>
        get() = _fileListLiveData
    val fileListStateful: Stateful<List<FileItem>>
        get() = _fileListLiveData.valueCompat

    fun reload() {
        val path = currentPath
        if (path.isArchivePath) {
            path.archiveRefresh()
        }
        _fileListLiveData.reload()
    }

    fun loadMoreSearchResults() = _fileListLiveData.loadMoreSearchResults()

    val searchViewExpandedLiveData = savedSearch.expanded
    var isSearchViewExpanded: Boolean
        get() = searchViewExpandedLiveData.valueCompat
        set(value) {
            if (searchViewExpandedLiveData.valueCompat == value) {
                return
            }
            searchViewExpandedLiveData.value = value
        }

    private val _searchViewQueryLiveData = savedSearch.viewQuery
    var searchViewQuery: String
        get() = _searchViewQueryLiveData.valueCompat
        set(value) {
            if (_searchViewQueryLiveData.valueCompat == value) {
                return
            }
            _searchViewQueryLiveData.value = value
        }

    private val breadcrumbLiveDataDelegate = lazy(LazyThreadSafetyMode.NONE) {
        BreadcrumbLiveData(trailLiveData)
    }
    val breadcrumbLiveData: LiveData<BreadcrumbData>
        get() = breadcrumbLiveDataDelegate.value
    val canNavigateUpBreadcrumb: Boolean
        get() = breadcrumbLiveData.valueCompat.selectedIndex > 0

    private val _viewTypeLiveData = FileViewTypeLiveData(currentPathLiveData)
    val viewTypeLiveData: LiveData<FileViewType> = _viewTypeLiveData
    var viewType: FileViewType
        get() = _viewTypeLiveData.value ?: Settings.FILE_LIST_VIEW_TYPE.valueCompat
        set(value) {
            setViewType(value, isViewSortPathSpecific)
        }

    fun setViewType(value: FileViewType, pathSpecific: Boolean) {
        _viewTypeLiveData.putValue(value, pathSpecific)
    }

    private val _gridColumnOverridesLiveData =
        GridColumnOverridesLiveData(currentPathLiveData)
    val gridColumnOverridesLiveData: LiveData<GridColumnOverrides> =
        _gridColumnOverridesLiveData
    val gridColumnOverrides: GridColumnOverrides
        get() = _gridColumnOverridesLiveData.value ?: GridColumnOverrides()

    fun setGridColumnOverride(
        widthClass: GridWidthClass,
        value: Int,
        pathSpecific: Boolean
    ) {
        _gridColumnOverridesLiveData.putValue(
            gridColumnOverrides.withValue(widthClass, value),
            pathSpecific
        )
    }

    private val _sortOptionsLiveData = FileSortOptionsLiveData(currentPathLiveData)
    val sortOptionsLiveData: LiveData<FileSortOptions> = _sortOptionsLiveData
    val sortOptions: FileSortOptions
        get() = _sortOptionsLiveData.valueCompat

    fun setSortBy(by: By, pathSpecific: Boolean = isViewSortPathSpecific) =
        _sortOptionsLiveData.putBy(by, pathSpecific)

    fun setSortOrder(order: Order, pathSpecific: Boolean = isViewSortPathSpecific) =
        _sortOptionsLiveData.putOrder(order, pathSpecific)

    fun setSortDirectoriesFirst(
        isDirectoriesFirst: Boolean,
        pathSpecific: Boolean = isViewSortPathSpecific
    ) = _sortOptionsLiveData.putIsDirectoriesFirst(isDirectoriesFirst, pathSpecific)

    private val _viewSortPathSpecificLiveData =
        FileViewSortPathSpecificLiveData(currentPathLiveData)
    val viewSortPathSpecificLiveData: LiveData<Boolean>
        get() = _viewSortPathSpecificLiveData
    var isViewSortPathSpecific: Boolean
        get() = _viewSortPathSpecificLiveData.valueCompat
        set(value) {
            setViewSortPathSpecificMode(value)
        }

    fun setViewSortPathSpecificMode(value: Boolean) {
        _viewSortPathSpecificLiveData.putValue(value)
    }

    private val _pickOptionsLiveData = MutableLiveData<PickOptions?>()
    val pickOptionsLiveData: LiveData<PickOptions?>
        get() = _pickOptionsLiveData
    var pickOptions: PickOptions?
        get() = _pickOptionsLiveData.value
        set(value) {
            _pickOptionsLiveData.value = value
        }

    var isCreateFileNameEditInitialized: Boolean = false

    private val _selectedFilesLiveData = MutableLiveData(fileItemSetOf())
    val selectedFilesLiveData: LiveData<FileItemSet>
        get() = _selectedFilesLiveData
    val selectedFiles: FileItemSet
        get() = _selectedFilesLiveData.valueCompat

    fun selectFile(file: FileItem, selected: Boolean) {
        selectFiles(fileItemSetOf(file), selected)
    }

    fun selectFiles(files: FileItemSet, selected: Boolean) {
        val selectedFiles = _selectedFilesLiveData.valueCompat
        if (selectedFiles === files) {
            if (!selected && selectedFiles.isNotEmpty()) {
                selectedFiles.clear()
                _selectedFilesLiveData.value = selectedFiles
            }
            return
        }
        val reduced = BrowserSelectionReducer.reduce(selectedFiles, files, selected)
        if (reduced != selectedFiles) {
            selectedFiles.clear()
            selectedFiles.addAll(reduced)
            _selectedFilesLiveData.value = selectedFiles
        }
    }

    fun replaceSelectedFiles(files: FileItemSet) {
        val selectedFiles = _selectedFilesLiveData.valueCompat
        if (selectedFiles == files) {
            return
        }
        selectedFiles.clear()
        selectedFiles.addAll(files)
        _selectedFilesLiveData.value = selectedFiles
    }

    fun clearSelectedFiles() {
        val selectedFiles = _selectedFilesLiveData.valueCompat
        if (selectedFiles.isEmpty()) {
            return
        }
        selectedFiles.clear()
        _selectedFilesLiveData.value = selectedFiles
    }

    val pasteStateLiveData: LiveData<PasteState> = _pasteStateLiveData
    val pasteState: PasteState
        get() = _pasteStateLiveData.valueCompat

    fun addToPasteState(copy: Boolean, files: FileItemSet) {
        val pasteState = _pasteStateLiveData.valueCompat
        var changed = false
        if (pasteState.copy != copy) {
            changed = pasteState.files.isNotEmpty()
            pasteState.files.clear()
            pasteState.copy = copy
        }
        changed = changed or pasteState.files.addAll(files)
        if (changed) {
            _pasteStateLiveData.value = pasteState
        }
    }

    fun clearPasteState() {
        val pasteState = _pasteStateLiveData.valueCompat
        if (pasteState.files.isEmpty()) {
            return
        }
        pasteState.files.clear()
        _pasteStateLiveData.value = pasteState
    }

    private val _isRequestingStorageAccessLiveData = MutableLiveData(false)
    var isStorageAccessRequested: Boolean
        get() = _isRequestingStorageAccessLiveData.valueCompat
        set(value) {
            _isRequestingStorageAccessLiveData.value = value
        }

    private val _isRequestingNotificationPermissionLiveData = MutableLiveData(false)
    var isNotificationPermissionRequested: Boolean
        get() = _isRequestingNotificationPermissionLiveData.valueCompat
        set(value) {
            _isRequestingNotificationPermissionLiveData.value = value
        }

    override fun onCleared() {
        _fileListLiveData.close()
    }

    companion object {
        private val _pasteStateLiveData = MutableLiveData(PasteState())
    }

    private class FileListSwitchMapLiveData(
        private val pathLiveData: LiveData<Path>,
        private val searchStateLiveData: LiveData<SearchState>
    ) : MediatorLiveData<Stateful<List<FileItem>>>(), Closeable {
        private var liveData: CloseableLiveData<Stateful<List<FileItem>>>? = null

        init {
            addSource(pathLiveData) { updateSource() }
            addSource(searchStateLiveData) { updateSource() }
        }

        private fun updateSource() {
            liveData?.let {
                removeSource(it)
                it.close()
            }
            val path = pathLiveData.value ?: run {
                liveData = null
                value = Loading(null)
                return
            }
            val searchState = searchStateLiveData.valueCompat
            val liveData = if (searchState.isSearching) {
                SearchFileListLiveData(path, searchState.query)
            } else {
                FileListLiveData(path)
            }
            this.liveData = liveData
            addSource(liveData) { value = it }
        }

        fun reload() {
            when (val liveData = liveData) {
                is FileListLiveData -> liveData.loadValue()
                is SearchFileListLiveData -> liveData.loadValue()
            }
        }

        fun loadMoreSearchResults() {
            (liveData as? SearchFileListLiveData)?.loadNextPage()
        }

        override fun close() {
            liveData?.let {
                removeSource(it)
                it.close()
                this.liveData = null
            }
        }
    }
}
