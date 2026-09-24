// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

internal data class BrowserTabState(
    val id: Long,
    var title: String,
    var dualPaneEnabled: Boolean = false,
    var activePane: BrowserPane = BrowserPane.PRIMARY,
    var dividerFraction: Float = DEFAULT_DIVIDER_FRACTION
) {
    val primaryPaneTag: String
        get() = fragmentTag(id)

    val secondaryPaneTag: String
        get() = secondaryFragmentTag(id)

    companion object {
        fun fragmentTag(id: Long): String = "browser_tab_$id"

        fun secondaryFragmentTag(id: Long): String = "browser_tab_${id}_secondary"

        const val DEFAULT_DIVIDER_FRACTION = 0.5f
    }
}

internal enum class BrowserPane { PRIMARY, SECONDARY }

/**
 * Lightweight state for the workspaces in one Android window.
 *
 * The fragments own their individual navigation/search/selection state. This controller owns only
 * ordering, naming and active-workspace identity so inactive fragments can be detached safely.
 */
internal class BrowserTabsController(
    private val maximumTabCount: Int = MAXIMUM_TAB_COUNT
) {
    private val mutableTabs = mutableListOf<BrowserTabState>()

    val tabs: List<BrowserTabState>
        get() = mutableTabs

    var activeId: Long? = null
        private set

    private var nextId = 0L

    val nextTabId: Long
        get() = nextId

    val activeTab: BrowserTabState?
        get() = mutableTabs.firstOrNull { it.id == activeId }

    val canAddTab: Boolean
        get() = mutableTabs.size < maximumTabCount

    fun initialize(title: String): BrowserTabState =
        add(title) ?: error("A new controller must accept its first tab")

    fun restore(
        ids: List<Long>,
        titles: List<String>,
        requestedActiveId: Long?,
        requestedNextId: Long,
        dualPaneEnabled: List<Boolean> = emptyList(),
        activePanes: List<BrowserPane> = emptyList(),
        dividerFractions: List<Float> = emptyList()
    ): Boolean {
        mutableTabs.clear()
        ids.zip(titles)
            .distinctBy { it.first }
            .take(maximumTabCount)
            .mapIndexedTo(mutableTabs) { index, (id, title) ->
                BrowserTabState(
                    id = id,
                    title = title,
                    dualPaneEnabled = dualPaneEnabled.getOrElse(index) { false },
                    activePane = activePanes.getOrElse(index) { BrowserPane.PRIMARY },
                    dividerFraction = dividerFractions.getOrElse(index) {
                        BrowserTabState.DEFAULT_DIVIDER_FRACTION
                    }.coerceIn(MINIMUM_DIVIDER_FRACTION, MAXIMUM_DIVIDER_FRACTION)
                )
            }
        if (mutableTabs.isEmpty()) {
            activeId = null
            nextId = 0L
            return false
        }
        activeId = requestedActiveId?.takeIf { id -> mutableTabs.any { it.id == id } }
            ?: mutableTabs.first().id
        nextId = maxOf(requestedNextId, (mutableTabs.maxOfOrNull { it.id } ?: -1L) + 1L)
        return true
    }

    fun add(title: String): BrowserTabState? {
        if (!canAddTab) {
            return null
        }
        val tab = BrowserTabState(nextId++, title)
        mutableTabs += tab
        activeId = tab.id
        return tab
    }

    fun select(id: Long): Boolean {
        if (activeId == id || mutableTabs.none { it.id == id }) {
            return false
        }
        activeId = id
        return true
    }

    fun updateTitle(id: Long, title: String): Boolean {
        val tab = mutableTabs.firstOrNull { it.id == id } ?: return false
        if (tab.title == title) {
            return false
        }
        tab.title = title
        return true
    }

    fun updateWorkspace(
        id: Long,
        dualPaneEnabled: Boolean? = null,
        activePane: BrowserPane? = null,
        dividerFraction: Float? = null
    ): BrowserTabState? {
        val tab = mutableTabs.firstOrNull { it.id == id } ?: return null
        dualPaneEnabled?.let { tab.dualPaneEnabled = it }
        activePane?.let { tab.activePane = it }
        dividerFraction?.let {
            tab.dividerFraction = it.coerceIn(
                MINIMUM_DIVIDER_FRACTION,
                MAXIMUM_DIVIDER_FRACTION
            )
        }
        return tab
    }

    /** Returns the newly active tab ID, or null when the final tab cannot be closed. */
    fun close(id: Long): Long? {
        if (mutableTabs.size <= 1) {
            return null
        }
        val index = mutableTabs.indexOfFirst { it.id == id }
        if (index < 0) {
            return null
        }
        val wasActive = activeId == id
        mutableTabs.removeAt(index)
        if (wasActive) {
            activeId = mutableTabs[index.coerceAtMost(mutableTabs.lastIndex)].id
        }
        return activeId
    }

    companion object {
        const val MAXIMUM_TAB_COUNT = 10
        const val MINIMUM_DIVIDER_FRACTION = 0.25f
        const val MAXIMUM_DIVIDER_FRACTION = 0.75f
    }
}
