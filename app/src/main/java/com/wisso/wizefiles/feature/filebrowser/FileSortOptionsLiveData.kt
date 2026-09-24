package com.wisso.wizefiles.feature.filebrowser

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import java.nio.file.Path
import com.wisso.wizefiles.feature.filebrowser.FileSortOptions.By
import com.wisso.wizefiles.feature.filebrowser.FileSortOptions.Order
import com.wisso.wizefiles.settings.PathSettings
import com.wisso.wizefiles.settings.SettingLiveData
import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.util.valueCompat

class FileSortOptionsLiveData(pathLiveData: LiveData<Path>) : MediatorLiveData<FileSortOptions>() {
    private lateinit var pathSortOptionsLiveData: SettingLiveData<FileSortOptions?>
    private lateinit var pathViewSortPathSpecificLiveData: SettingLiveData<Boolean>

    private fun loadValue() {
        if (!this::pathSortOptionsLiveData.isInitialized ||
            !this::pathViewSortPathSpecificLiveData.isInitialized
        ) {
            // Not yet initialized.
            return
        }
        val value = FileViewSortPersistencePolicy.effective(
            pathViewSortPathSpecificLiveData.valueCompat,
            pathSortOptionsLiveData.value,
            Settings.FILE_LIST_SORT_OPTIONS.valueCompat
        )
        if (this.value != value) {
            this.value = value
        }
    }

    fun putBy(by: By, pathSpecific: Boolean = pathSortOptionsLiveData.value != null) {
        putValue(valueCompat.copy(by = by), pathSpecific)
    }

    fun putOrder(order: Order, pathSpecific: Boolean = pathSortOptionsLiveData.value != null) {
        putValue(valueCompat.copy(order = order), pathSpecific)
    }

    fun putIsDirectoriesFirst(
        isDirectoriesFirst: Boolean,
        pathSpecific: Boolean = pathSortOptionsLiveData.value != null
    ) {
        putValue(valueCompat.copy(isDirectoriesFirst = isDirectoriesFirst), pathSpecific)
    }

    private fun putValue(value: FileSortOptions, pathSpecific: Boolean) {
        when (FileViewSortPersistencePolicy.target(pathSpecific)) {
            FileViewSortPersistenceTarget.PATH -> pathSortOptionsLiveData.putValue(value)
            FileViewSortPersistenceTarget.GLOBAL ->
                Settings.FILE_LIST_SORT_OPTIONS.putValue(value)
        }
    }

    init {
        addSource(Settings.FILE_LIST_SORT_OPTIONS) { loadValue() }
        addSource(pathLiveData) { path: Path ->
            if (this::pathSortOptionsLiveData.isInitialized) {
                removeSource(pathSortOptionsLiveData)
            }
            if (this::pathViewSortPathSpecificLiveData.isInitialized) {
                removeSource(pathViewSortPathSpecificLiveData)
            }
            pathSortOptionsLiveData = PathSettings.getFileListSortOptions(path)
            pathViewSortPathSpecificLiveData = PathSettings.getFileListViewSortPathSpecific(path)
            addSource(pathSortOptionsLiveData) { loadValue() }
            addSource(pathViewSortPathSpecificLiveData) { loadValue() }
        }
    }
}
