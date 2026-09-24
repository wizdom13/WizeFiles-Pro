package com.wisso.wizefiles.feature.filebrowser

import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SearchView
import com.wisso.wizefiles.ui.FixQueryChangeSearchView
import com.wisso.wizefiles.util.DebouncedRunnable

internal class FileListSearchController(
    private val isHostResumed: () -> Boolean,
    private val activeViewModel: () -> FileListViewModel
) {
    private val debouncedSearch = DebouncedRunnable(Handler(Looper.getMainLooper()), 150) {
        val target = activeViewModel()
        if (!isHostResumed() || !target.isSearchViewExpanded) {
            return@DebouncedRunnable
        }
        val query = target.searchViewQuery
        if (hasMinimumLength(query)) {
            target.search(query)
        }
    }

    private var searchItem: MenuItem? = null
    private var searchView: FixQueryChangeSearchView? = null
    private var preserveSessionOnCollapse = false

    val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (searchView?.hideImePreservingSearch() != true) {
                collapse()
            }
        }
    }

    fun bind(item: MenuItem) {
        releaseViewBindings()
        val view = item.actionView as FixQueryChangeSearchView
        searchItem = item
        searchView = view

        // MenuItem.OnActionExpandListener.onMenuItemActionExpand() is called before SearchView
        // resets the query.
        view.setOnSearchClickListener {
            val target = activeViewModel()
            target.isSearchViewExpanded = true
            view.setQuery(target.searchViewQuery, false)
            debouncedSearch()
        }
        // SearchView.OnCloseListener.onClose() is not always called.
        item.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                if (view.hideImePreservingSearch()) {
                    return false
                }
                if (preserveSessionOnCollapse) {
                    return true
                }
                val target = activeViewModel()
                target.isSearchViewExpanded = false
                target.stopSearching()
                return true
            }
        })
        view.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                val target = activeViewModel()
                debouncedSearch.cancel()
                val normalizedQuery = normalizedQuery(query)
                if (normalizedQuery == null) {
                    target.stopSearching()
                } else {
                    target.search(normalizedQuery)
                }
                return true
            }

            override fun onQueryTextChange(query: String): Boolean {
                if (view.shouldIgnoreQueryChange) {
                    return false
                }
                val target = activeViewModel()
                target.searchViewQuery = query
                if (!hasMinimumLength(query)) {
                    debouncedSearch.cancel()
                    target.stopSearching()
                } else {
                    debouncedSearch()
                }
                return false
            }
        })
        if (activeViewModel().isSearchViewExpanded) {
            item.expandActionView()
        }
    }

    fun syncToActivePane() {
        val target = activeViewModel()
        searchView?.setQuery(target.searchViewQuery, false)
        val item = searchItem
        if (target.isSearchViewExpanded && item != null && !item.isActionViewExpanded) {
            item.expandActionView()
        }
    }

    fun collapse(preserveSession: Boolean = false) {
        val item = searchItem ?: return
        if (!item.isActionViewExpanded) return
        preserveSessionOnCollapse = preserveSession
        try {
            item.collapseActionView()
        } finally {
            preserveSessionOnCollapse = false
        }
    }

    fun hideImePreservingSearch() {
        if (searchItem?.isActionViewExpanded == true) {
            searchView?.hideImePreservingSearch()
        }
    }

    fun setBackHandlingEnabled(enabled: Boolean) {
        onBackPressedCallback.isEnabled = enabled
    }

    fun release() {
        debouncedSearch.cancel()
        onBackPressedCallback.isEnabled = false
        releaseViewBindings()
    }

    private fun releaseViewBindings() {
        searchView?.setOnSearchClickListener(null)
        searchView?.setOnQueryTextListener(null)
        searchItem?.setOnActionExpandListener(null)
        searchView = null
        searchItem = null
        preserveSessionOnCollapse = false
    }

    companion object {
        internal fun normalizedQuery(query: String): String? =
            query.trim().takeIf { it.length >= MINIMUM_QUERY_LENGTH }

        private fun hasMinimumLength(query: String): Boolean =
            query.trim().length >= MINIMUM_QUERY_LENGTH

        private const val MINIMUM_QUERY_LENGTH = 2
    }
}
