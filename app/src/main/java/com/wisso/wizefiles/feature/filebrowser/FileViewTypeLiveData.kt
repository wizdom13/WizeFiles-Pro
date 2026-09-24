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

class FileViewTypeLiveData(pathLiveData: LiveData<Path>) : MediatorLiveData<FileViewType>() {
    private lateinit var pathViewTypeLiveData: SettingLiveData<FileViewType?>
    private lateinit var pathViewSortPathSpecificLiveData: SettingLiveData<Boolean>

    private fun loadValue() {
        val pathInitialized = this::pathViewTypeLiveData.isInitialized &&
            this::pathViewSortPathSpecificLiveData.isInitialized
        val value = FileViewSortPersistencePolicy.effective(
            pathSpecific = pathInitialized && pathViewSortPathSpecificLiveData.valueCompat,
            pathValue = if (pathInitialized) pathViewTypeLiveData.value else null,
            globalValue = Settings.FILE_LIST_VIEW_TYPE.valueCompat
        )
        if (this.value != value) {
            this.value = value
        }
    }

    fun putValue(value: FileViewType, pathSpecific: Boolean = pathViewTypeLiveData.value != null) {
        when (FileViewSortPersistencePolicy.target(pathSpecific)) {
            FileViewSortPersistenceTarget.PATH -> pathViewTypeLiveData.putValue(value)
            FileViewSortPersistenceTarget.GLOBAL -> Settings.FILE_LIST_VIEW_TYPE.putValue(value)
        }
    }

    init {
        value = Settings.FILE_LIST_VIEW_TYPE.valueCompat
        addSource(Settings.FILE_LIST_VIEW_TYPE) { loadValue() }
        addSource(pathLiveData) { path: Path ->
            if (this::pathViewTypeLiveData.isInitialized) {
                removeSource(pathViewTypeLiveData)
            }
            if (this::pathViewSortPathSpecificLiveData.isInitialized) {
                removeSource(pathViewSortPathSpecificLiveData)
            }
            pathViewTypeLiveData = PathSettings.getFileListViewType(path)
            pathViewSortPathSpecificLiveData = PathSettings.getFileListViewSortPathSpecific(path)
            addSource(pathViewTypeLiveData) { loadValue() }
            addSource(pathViewSortPathSpecificLiveData) { loadValue() }
        }
    }
}
