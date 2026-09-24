package com.wisso.wizefiles.navigation

import androidx.lifecycle.MediatorLiveData
import java.nio.file.Path
import com.wisso.wizefiles.provider.archive.isArchivePath
import com.wisso.wizefiles.util.valueCompat

object NavigationRootMapLiveData : MediatorLiveData<Map<Path, NavigationRoot>>() {
    init {
        // Initialize value before we have any active observer.
        loadValue()
        addSource(NavigationItemListLiveData) { loadValue() }
    }

    private fun loadValue() {
        value = NavigationItemListLiveData.value.orEmpty()
            .mapNotNull { it as? NavigationRoot }
            .associateBy { it.path }
    }
}

fun findNavigationRoot(path: Path, navigationRootMap: Map<Path, NavigationRoot>): NavigationRoot? {
    // Archive paths have their own virtual root, whose textual form is also "/". Never compare
    // that virtual root with physical navigation roots such as Device Root by path string.
    if (path.isArchivePath) {
        return null
    }
    navigationRootMap[path]?.let { return it }
    val normalizedPath = path.toPathLookupKey()
    return navigationRootMap.entries.firstOrNull { (rootPath, _) ->
        rootPath.toPathLookupKey() == normalizedPath
    }?.value
}

private fun Path.toPathLookupKey(): String {
    val pathString = toString().trimEnd('/')
    return if (pathString.isEmpty()) "/" else pathString
}
