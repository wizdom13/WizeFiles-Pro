package com.wisso.wizefiles.navigation

import androidx.lifecycle.MediatorLiveData
import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.storage.StorageVolumeListLiveData

object NavigationItemListLiveData : MediatorLiveData<List<NavigationItem?>>() {
    init {
        // Initialize value before we have any active observer.
        loadValue()
        addSource(Settings.STORAGES) { loadValue() }
        addSource(StorageVolumeListLiveData) { loadValue() }
        addSource(StandardDirectoriesLiveData) { loadValue() }
        addSource(Settings.BOOKMARK_DIRECTORIES) { loadValue() }
        addSource(Settings.RECYCLE_BIN) { loadValue() }
    }

    private fun loadValue() {
        value = navigationItems
    }
}
