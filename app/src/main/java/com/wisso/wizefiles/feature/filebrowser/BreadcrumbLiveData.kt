// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import java.nio.file.Path
import com.wisso.wizefiles.R
import com.wisso.wizefiles.recyclebin.RecycleBinManager
import com.wisso.wizefiles.navigation.findNavigationRoot
import com.wisso.wizefiles.navigation.NavigationRootMapLiveData
import com.wisso.wizefiles.util.valueCompat

class BreadcrumbLiveData(
    private val trailLiveData: LiveData<TrailData>
) : MediatorLiveData<BreadcrumbData>() {
    init {
        addSource(trailLiveData) { loadValue() }
        addSource(NavigationRootMapLiveData) { loadValue() }
    }

    private fun loadValue() {
        val navigationRootMap = NavigationRootMapLiveData.value ?: return
        val trailData = trailLiveData.value ?: return
        val paths = mutableListOf<Path>()
        val nameProducers = mutableListOf<(Context) -> String>()
        val iconResIds = mutableListOf<Int?>()
        var selectedIndex = trailData.currentIndex
        for (path in trailData.trail) {
            val navigationRoot = findNavigationRoot(path, navigationRootMap)
            val itemCount = nameProducers.size
            if (navigationRoot != null && selectedIndex >= itemCount) {
                selectedIndex -= itemCount
                paths.clear()
                paths.add(navigationRoot.path)
                nameProducers.clear()
                nameProducers.add { navigationRoot.getName(it) }
                iconResIds.clear()
                iconResIds.add(navigationRoot.iconRes)
            } else {
                paths.add(path)
                nameProducers.add { context ->
                    if (RecycleBinManager.isRecycleBinRootPath(path)) {
                        context.getString(R.string.navigation_recycle_bin)
                    } else {
                        path.name
                    }
                }
                iconResIds.add(null)
            }
        }
        value = BreadcrumbData(paths, nameProducers, iconResIds, selectedIndex)
    }
}
