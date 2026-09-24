// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.settings

import android.graphics.drawable.NinePatchDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.h6ah4i.android.widget.advrecyclerview.animator.DraggableItemAnimator
import com.h6ah4i.android.widget.advrecyclerview.draggable.RecyclerViewDragDropManager
import com.h6ah4i.android.widget.advrecyclerview.utils.WrapperAdapterUtils
import com.wisso.wizefiles.databinding.FragmentBookmarkDirectoryListBinding
import com.wisso.wizefiles.feature.filebrowser.FileListActivity
import com.wisso.wizefiles.navigation.BookmarkDirectories
import com.wisso.wizefiles.navigation.BookmarkDirectory
import com.wisso.wizefiles.navigation.EditBookmarkDirectoryDialogActivity
import com.wisso.wizefiles.navigation.EditBookmarkDirectoryDialogFragment
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.ui.ScrollingViewOnApplyWindowInsetsListener
import com.wisso.wizefiles.util.createIntent
import com.wisso.wizefiles.util.fadeToVisibilityUnsafe
import com.wisso.wizefiles.util.getDrawable
import com.wisso.wizefiles.util.launchSafe
import com.wisso.wizefiles.util.putArgs
import com.wisso.wizefiles.util.startActivitySafe

class BookmarkDirectoryListFragment : Fragment(), BookmarkDirectoryListAdapter.Listener {
    private val openPathLauncher =
        registerForActivityResult(FileListActivity.OpenDirectoryContract(), ::onOpenPathResult)

    private lateinit var binding: FragmentBookmarkDirectoryListBinding

    private lateinit var adapter: BookmarkDirectoryListAdapter
    private lateinit var dragDropManager: RecyclerViewDragDropManager
    private lateinit var wrappedAdapter: RecyclerView.Adapter<*>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        FragmentBookmarkDirectoryListBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(
            activity, RecyclerView.VERTICAL, false
        )
        adapter = BookmarkDirectoryListAdapter(this)
        dragDropManager = RecyclerViewDragDropManager().apply {
            setDraggingItemShadowDrawable(
                getDrawable(
                    com.h6ah4i.android.materialshadowninepatch.R.drawable.ms9_composite_shadow_z2
                ) as NinePatchDrawable
            )
        }
        wrappedAdapter = dragDropManager.createWrappedAdapter(adapter)
        binding.recyclerView.adapter = wrappedAdapter
        binding.recyclerView.itemAnimator = DraggableItemAnimator()
        dragDropManager.attachRecyclerView(binding.recyclerView)
        binding.recyclerView.setOnApplyWindowInsetsListener(
            ScrollingViewOnApplyWindowInsetsListener(binding.recyclerView)
        )
        binding.fab.setOnClickListener { onAddBookmarkDirectory() }

        Settings.BOOKMARK_DIRECTORIES.observe(viewLifecycleOwner) {
            onBookmarkDirectoryListChanged(it)
        }
    }

    override fun onPause() {
        super.onPause()

        dragDropManager.cancelDrag()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        dragDropManager.release()
        WrapperAdapterUtils.releaseAll(wrappedAdapter)
    }

    private fun onBookmarkDirectoryListChanged(bookmarkDirectories: List<BookmarkDirectory>) {
        binding.emptyView.fadeToVisibilityUnsafe(bookmarkDirectories.isEmpty())
        adapter.replace(bookmarkDirectories)
    }

    private fun onAddBookmarkDirectory() {
        openPathLauncher.launchSafe(null, this)
    }

    private fun onOpenPathResult(result: AppPath?) {
        val path = result ?: return
        BookmarkDirectories.add(BookmarkDirectory(null, path))
    }

    override fun editBookmarkDirectory(bookmarkDirectory: BookmarkDirectory) {
        startActivitySafe(
            EditBookmarkDirectoryDialogActivity::class.createIntent()
                .putArgs(EditBookmarkDirectoryDialogFragment.Args(bookmarkDirectory))
        )
    }

    override fun moveBookmarkDirectory(fromPosition: Int, toPosition: Int) {
        BookmarkDirectories.move(fromPosition, toPosition)
    }
}
