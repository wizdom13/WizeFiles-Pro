// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

internal enum class BrowserLoadPhase { LOADING, SUCCESS, FAILURE }

internal sealed interface BrowserSubtitle {
    data object Loading : BrowserSubtitle
    data object Error : BrowserSubtitle
    data class Counts(val directories: Int, val files: Int) : BrowserSubtitle
}

internal data class BrowserListPresentation(
    val subtitle: BrowserSubtitle,
    val showRefresh: Boolean,
    val showBlockingProgress: Boolean,
    val showError: Boolean,
    val showEmpty: Boolean
)

/** Pure rendering decision; the Fragment only translates it to Android views and resources. */
internal object BrowserListPresentationPolicy {
    fun decide(
        phase: BrowserLoadPhase,
        counts: FileListSubtitleCounts?,
        isSearching: Boolean
    ): BrowserListPresentation {
        val hasItems = counts != null && counts.directories + counts.files > 0
        val loading = phase == BrowserLoadPhase.LOADING
        val subtitle = when {
            phase == BrowserLoadPhase.FAILURE -> BrowserSubtitle.Error
            loading && !isSearching -> BrowserSubtitle.Loading
            else -> BrowserSubtitle.Counts(counts?.directories ?: 0, counts?.files ?: 0)
        }
        return BrowserListPresentation(
            subtitle = subtitle,
            showRefresh = loading && (hasItems || isSearching),
            showBlockingProgress = loading && !(hasItems || isSearching),
            showError = phase == BrowserLoadPhase.FAILURE && !hasItems,
            showEmpty = phase == BrowserLoadPhase.SUCCESS && !hasItems
        )
    }
}
