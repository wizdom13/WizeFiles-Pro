// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import androidx.lifecycle.SavedStateHandle

/**
 * Defines the process-death boundary for browser state.
 *
 * Paths may embed remote endpoints, document grants, or user information and selected [FileItem]
 * instances may refer to grants that died with the process. Consequently only provider-neutral UI
 * state is durable. Navigation is rebuilt by the activity's trusted launch route and selection is
 * deliberately empty after recreation.
 */
internal object BrowserProcessRestorationPolicy {
    val restoresNavigationTrail: Boolean = false
    val restoresSelection: Boolean = false

    const val IS_SEARCHING = "browser.isSearching"
    const val SEARCH_QUERY = "browser.searchQuery"
    const val SEARCH_EXPANDED = "browser.searchExpanded"
    const val SEARCH_VIEW_QUERY = "browser.searchViewQuery"

    fun durableKeys(): Set<String> = setOf(IS_SEARCHING, SEARCH_QUERY, SEARCH_EXPANDED, SEARCH_VIEW_QUERY)

    /** Drops accidentally registered provider state before any browser model observes it. */
    fun sanitize(handle: SavedStateHandle) {
        handle.keys().filterNot { it in durableKeys() }.forEach { handle.remove<Any?>(it) }
    }
}
