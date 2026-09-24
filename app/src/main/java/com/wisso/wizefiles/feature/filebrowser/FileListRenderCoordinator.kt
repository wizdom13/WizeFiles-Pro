// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import android.os.Parcelable
import android.text.TextUtils
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wisso.wizefiles.core.files.model.FileItem

internal class FileListRenderCoordinator(
    private val calculateSpanCount: (FileViewType) -> Int
) {
    private var recyclerView: RecyclerView? = null
    private var layoutManager: GridLayoutManager? = null
    private var adapter: FileListAdapter? = null
    private var attached = false

    private var desiredViewType: FileViewType? = null
    private var appliedViewType: FileViewType? = null
    private var desiredSortOptions: FileSortOptions? = null
    private var appliedSortOptions: FileSortOptions? = null
    private var pendingSpanCountUpdate = false
    private val listUpdates = FileListRenderQueue()
    private var appliedIsSearching: Boolean? = null
    private var pendingSelectedFiles: FileItemSet? = null
    private var hasPendingPickOptions = false
    private var pendingPickOptions: PickOptions? = null
    private var pendingNameEllipsize: TextUtils.TruncateAt? = null
    private var pendingLayoutState: Parcelable? = null

    private val flushRunnable = Runnable { flush() }
    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                flush()
            }
        }
    }

    fun attach(
        recyclerView: RecyclerView,
        layoutManager: GridLayoutManager,
        adapter: FileListAdapter
    ) {
        detach()
        this.recyclerView = recyclerView
        this.layoutManager = layoutManager
        this.adapter = adapter
        recyclerView.addOnScrollListener(scrollListener)
        attached = true
    }

    fun detach() {
        attached = false
        recyclerView?.let { view ->
            view.removeCallbacks(flushRunnable)
            view.removeOnScrollListener(scrollListener)
            view.stopScroll()
            view.itemAnimator?.endAnimations()
            for (index in 0..<view.childCount) {
                view.getChildAt(index).clearAnimation()
            }
            view.adapter = null
        }
        clearPending()
        adapter = null
        layoutManager = null
        recyclerView = null
    }

    fun setViewType(viewType: FileViewType) {
        desiredViewType = viewType
        pendingSpanCountUpdate = true
        schedule()
    }

    fun requestSpanCountUpdate() {
        pendingSpanCountUpdate = true
        schedule()
    }

    fun setSortOptions(sortOptions: FileSortOptions) {
        desiredSortOptions = sortOptions
        schedule()
    }

    fun setPendingLayoutState(layoutState: Parcelable?) {
        pendingLayoutState = layoutState
    }

    fun replaceFiles(files: List<FileItem>, isSearching: Boolean) {
        listUpdates.replace(files, isSearching)
        schedule()
    }

    fun clearFiles() {
        listUpdates.clear()
        schedule()
    }

    fun setSelectedFiles(files: FileItemSet) {
        pendingSelectedFiles = FileItemSet().apply { addAll(files) }
        schedule()
    }

    fun setPickOptions(pickOptions: PickOptions?) {
        pendingPickOptions = pickOptions
        hasPendingPickOptions = true
        schedule()
    }

    fun setNameEllipsize(nameEllipsize: TextUtils.TruncateAt) {
        pendingNameEllipsize = nameEllipsize
        schedule()
    }

    private fun clearPending() {
        desiredViewType = null
        appliedViewType = null
        desiredSortOptions = null
        appliedSortOptions = null
        pendingSpanCountUpdate = false
        listUpdates.reset()
        appliedIsSearching = null
        pendingSelectedFiles = null
        hasPendingPickOptions = false
        pendingPickOptions = null
        pendingNameEllipsize = null
        pendingLayoutState = null
    }

    private fun schedule() {
        if (!attached) return
        val recyclerView = recyclerView ?: return
        if (recyclerView.scrollState != RecyclerView.SCROLL_STATE_IDLE) return
        if (recyclerView.isComputingLayout) {
            recyclerView.removeCallbacks(flushRunnable)
            recyclerView.postOnAnimation(flushRunnable)
            return
        }
        flush()
    }

    private fun flush() {
        if (!attached) return
        val recyclerView = recyclerView ?: return
        val layoutManager = layoutManager ?: return
        val adapter = adapter ?: return
        if (recyclerView.scrollState != RecyclerView.SCROLL_STATE_IDLE) return
        if (recyclerView.isComputingLayout) {
            recyclerView.removeCallbacks(flushRunnable)
            recyclerView.postOnAnimation(flushRunnable)
            return
        }
        recyclerView.removeCallbacks(flushRunnable)

        val viewType = desiredViewType
        val sortOptions = desiredSortOptions
        val viewTypeChanged = viewType != null && viewType != appliedViewType
        val sortOptionsChanged = sortOptions != null && sortOptions != appliedSortOptions
        val spanViewType = viewType ?: appliedViewType
        val updateSpanCount = pendingSpanCountUpdate && spanViewType != null
        pendingSpanCountUpdate = pendingSpanCountUpdate && spanViewType == null

        val listUpdate = listUpdates.take()
        val searchModeChanged = listUpdate is FileListRenderQueue.ListUpdate.Replace &&
            appliedIsSearching != listUpdate.isSearching
        if (
            viewTypeChanged || sortOptionsChanged || updateSpanCount ||
            listUpdate !is FileListRenderQueue.ListUpdate.None || searchModeChanged
        ) {
            recyclerView.stopScroll()
            recyclerView.itemAnimator?.endAnimations()
        }
        if (updateSpanCount) {
            val targetSpanCount = calculateSpanCount(requireNotNull(spanViewType))
            val spanCountChanged = layoutManager.spanCount != targetSpanCount
            if (spanCountChanged) {
                layoutManager.spanCount = targetSpanCount
                if (!viewTypeChanged && spanViewType == FileViewType.GRID) {
                    recyclerView.recycledViewPool.clear()
                    adapter.recreateGridViewHoldersForSpanChange()
                    recyclerView.requestLayout()
                }
            }
        }
        if (viewTypeChanged) {
            adapter.viewType = requireNotNull(viewType)
            appliedViewType = viewType
        }
        if (sortOptionsChanged) {
            adapter.sortOptions = requireNotNull(sortOptions)
            appliedSortOptions = sortOptions
        }
        when (listUpdate) {
            FileListRenderQueue.ListUpdate.None -> Unit
            FileListRenderQueue.ListUpdate.Clear -> {
                adapter.clear()
                appliedIsSearching = null
            }
            is FileListRenderQueue.ListUpdate.Replace -> {
                adapter.replaceListAndIsSearching(listUpdate.files, listUpdate.isSearching)
                appliedIsSearching = listUpdate.isSearching
            }
        }
        if (hasPendingPickOptions) {
            val pickOptions = pendingPickOptions
            hasPendingPickOptions = false
            pendingPickOptions = null
            adapter.pickOptions = pickOptions
        }
        pendingNameEllipsize?.let {
            pendingNameEllipsize = null
            adapter.nameEllipsize = it
        }
        pendingSelectedFiles?.let {
            pendingSelectedFiles = null
            adapter.replaceSelectedFiles(it)
        }
        if (listUpdate !is FileListRenderQueue.ListUpdate.None) {
            pendingLayoutState?.let(layoutManager::onRestoreInstanceState)
            pendingLayoutState = null
        }
    }
}

internal class FileListRenderQueue {
    sealed interface ListUpdate {
        data object None : ListUpdate

        data object Clear : ListUpdate

        data class Replace(
            val files: List<FileItem>,
            val isSearching: Boolean
        ) : ListUpdate
    }

    private var pending: ListUpdate = ListUpdate.None

    fun replace(files: List<FileItem>, isSearching: Boolean) {
        pending = ListUpdate.Replace(files.toList(), isSearching)
    }

    fun clear() {
        pending = ListUpdate.Clear
    }

    fun take(): ListUpdate = pending.also { pending = ListUpdate.None }

    fun reset() {
        pending = ListUpdate.None
    }
}
