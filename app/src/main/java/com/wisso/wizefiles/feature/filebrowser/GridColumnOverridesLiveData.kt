// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import com.wisso.wizefiles.settings.PathSettings
import com.wisso.wizefiles.settings.SettingLiveData
import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.util.valueCompat
import java.nio.file.Path

class GridColumnOverridesLiveData(
    pathLiveData: LiveData<Path>
) : MediatorLiveData<GridColumnOverrides>() {
    private lateinit var pathGridColumnOverridesLiveData:
        SettingLiveData<GridColumnOverrides?>
    private lateinit var pathViewSortPathSpecificLiveData: SettingLiveData<Boolean>

    private fun loadValue() {
        val pathInitialized = this::pathGridColumnOverridesLiveData.isInitialized &&
            this::pathViewSortPathSpecificLiveData.isInitialized
        val value = FileViewSortPersistencePolicy.effective(
            pathSpecific = pathInitialized && pathViewSortPathSpecificLiveData.valueCompat,
            pathValue = if (pathInitialized) pathGridColumnOverridesLiveData.value else null,
            globalValue = Settings.FILE_LIST_GRID_COLUMN_OVERRIDES.valueCompat
        )
        if (this.value != value) {
            this.value = value
        }
    }

    fun putValue(value: GridColumnOverrides, pathSpecific: Boolean) {
        when (FileViewSortPersistencePolicy.target(pathSpecific)) {
            FileViewSortPersistenceTarget.PATH ->
                pathGridColumnOverridesLiveData.putValue(value)
            FileViewSortPersistenceTarget.GLOBAL ->
                Settings.FILE_LIST_GRID_COLUMN_OVERRIDES.putValue(value)
        }
    }

    init {
        value = Settings.FILE_LIST_GRID_COLUMN_OVERRIDES.valueCompat
        addSource(Settings.FILE_LIST_GRID_COLUMN_OVERRIDES) { loadValue() }
        addSource(pathLiveData) { path ->
            if (this::pathGridColumnOverridesLiveData.isInitialized) {
                removeSource(pathGridColumnOverridesLiveData)
            }
            if (this::pathViewSortPathSpecificLiveData.isInitialized) {
                removeSource(pathViewSortPathSpecificLiveData)
            }
            pathGridColumnOverridesLiveData = PathSettings.getFileListGridColumnOverrides(path)
            pathViewSortPathSpecificLiveData =
                PathSettings.getFileListViewSortPathSpecific(path)
            addSource(pathGridColumnOverridesLiveData) { loadValue() }
            addSource(pathViewSortPathSpecificLiveData) { loadValue() }
        }
    }
}
