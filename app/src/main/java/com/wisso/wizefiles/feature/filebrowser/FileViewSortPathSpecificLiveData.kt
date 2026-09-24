// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import java.nio.file.Path
import com.wisso.wizefiles.settings.PathSettings
import com.wisso.wizefiles.settings.SettingLiveData
import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.util.valueCompat

class FileViewSortPathSpecificLiveData(pathLiveData: LiveData<Path>) : MediatorLiveData<Boolean>() {
    private lateinit var pathViewTypeLiveData: SettingLiveData<FileViewType?>
    private lateinit var pathGridColumnOverridesLiveData:
        SettingLiveData<GridColumnOverrides?>
    private lateinit var pathSortOptionsLiveData: SettingLiveData<FileSortOptions?>
    private lateinit var pathViewSortPathSpecificLiveData: SettingLiveData<Boolean>

    private fun loadValue() {
        if (!this::pathViewSortPathSpecificLiveData.isInitialized) {
            return
        }
        val value = pathViewSortPathSpecificLiveData.valueCompat
        if (this.value != value) {
            this.value = value
        }
    }

    fun putValue(value: Boolean) {
        pathViewSortPathSpecificLiveData.putValue(value)
        if (value) {
            if (pathViewTypeLiveData.value == null) {
                pathViewTypeLiveData.putValue(
                    FileViewSortPersistencePolicy.initializeOverride(
                        null,
                        Settings.FILE_LIST_VIEW_TYPE.valueCompat
                    )
                )
            }
            if (pathGridColumnOverridesLiveData.value == null) {
                pathGridColumnOverridesLiveData.putValue(
                    FileViewSortPersistencePolicy.initializeOverride(
                        null,
                        Settings.FILE_LIST_GRID_COLUMN_OVERRIDES.valueCompat
                    )
                )
            }
            if (pathSortOptionsLiveData.value == null) {
                pathSortOptionsLiveData.putValue(
                    FileViewSortPersistencePolicy.initializeOverride(
                        null,
                        Settings.FILE_LIST_SORT_OPTIONS.valueCompat
                    )
                )
            }
        } else {
            if (pathViewTypeLiveData.value != null) {
                pathViewTypeLiveData.putValue(null)
            }
            if (pathGridColumnOverridesLiveData.value != null) {
                pathGridColumnOverridesLiveData.putValue(null)
            }
            if (pathSortOptionsLiveData.value != null) {
                pathSortOptionsLiveData.putValue(null)
            }
        }
    }

    init {
        addSource(pathLiveData) { path: Path ->
            if (this::pathViewTypeLiveData.isInitialized) {
                removeSource(pathViewTypeLiveData)
            }
            if (this::pathGridColumnOverridesLiveData.isInitialized) {
                removeSource(pathGridColumnOverridesLiveData)
            }
            if (this::pathSortOptionsLiveData.isInitialized) {
                removeSource(pathSortOptionsLiveData)
            }
            if (this::pathViewSortPathSpecificLiveData.isInitialized) {
                removeSource(pathViewSortPathSpecificLiveData)
            }
            pathViewTypeLiveData = PathSettings.getFileListViewType(path)
            pathGridColumnOverridesLiveData =
                PathSettings.getFileListGridColumnOverrides(path)
            pathSortOptionsLiveData = PathSettings.getFileListSortOptions(path)
            pathViewSortPathSpecificLiveData = PathSettings.getFileListViewSortPathSpecific(path)
            addSource(pathViewTypeLiveData) { loadValue() }
            addSource(pathGridColumnOverridesLiveData) { loadValue() }
            addSource(pathSortOptionsLiveData) { loadValue() }
            addSource(pathViewSortPathSpecificLiveData) { loadValue() }
        }
    }
}
