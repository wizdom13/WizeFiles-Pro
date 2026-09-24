package com.wisso.wizefiles.feature.filebrowser

import androidx.lifecycle.SavedStateHandle

internal class BrowserSearchSavedState(private val handle: SavedStateHandle) {
    var isSearching: Boolean
        get() = handle[BrowserProcessRestorationPolicy.IS_SEARCHING] ?: false
        set(value) { handle[BrowserProcessRestorationPolicy.IS_SEARCHING] = value }

    var query: String
        get() = handle[BrowserProcessRestorationPolicy.SEARCH_QUERY] ?: ""
        set(value) { handle[BrowserProcessRestorationPolicy.SEARCH_QUERY] = value }

    val expanded = handle.getLiveData(BrowserProcessRestorationPolicy.SEARCH_EXPANDED, false)
    val viewQuery = handle.getLiveData(BrowserProcessRestorationPolicy.SEARCH_VIEW_QUERY, "")

    fun stop() {
        isSearching = false
        query = ""
    }
}
