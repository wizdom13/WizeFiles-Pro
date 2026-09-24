// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.navigation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wisso.wizefiles.R
import com.wisso.wizefiles.databinding.ItemNavigationHeaderBinding

class NavigationHeaderAdapter :
    RecyclerView.Adapter<NavigationHeaderAdapter.HeaderHolder>() {

    private var recyclerView: RecyclerView? = null
    private val layoutChangeListener = View.OnLayoutChangeListener {
            view, _, _, _, _, _, _, _, _ ->
        updateVisibleHeader(view as RecyclerView)
    }

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderHolder =
        HeaderHolder(
            ItemNavigationHeaderBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: HeaderHolder, position: Int) {
        recyclerView?.let(holder::extendIntoTopEdge)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
        recyclerView.addOnLayoutChangeListener(layoutChangeListener)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        recyclerView.removeOnLayoutChangeListener(layoutChangeListener)
        this.recyclerView = null
        super.onDetachedFromRecyclerView(recyclerView)
    }

    override fun getItemCount(): Int = 1

    override fun getItemId(position: Int): Long = HEADER_ID

    private fun updateVisibleHeader(recyclerView: RecyclerView) {
        val header = recyclerView.findViewHolderForAdapterPosition(0) as? HeaderHolder ?: return
        header.extendIntoTopEdge(recyclerView)
    }

    class HeaderHolder(binding: ItemNavigationHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun extendIntoTopEdge(recyclerView: RecyclerView) {
            val topExtension = recyclerView.paddingTop
            val contentHeight = itemView.resources.getDimensionPixelSize(
                R.dimen.navigation_header_height
            )
            val layoutParams = itemView.layoutParams as? ViewGroup.MarginLayoutParams ?: return
            val targetHeight = contentHeight + topExtension
            if (layoutParams.height == targetHeight &&
                layoutParams.topMargin == -topExtension
            ) {
                return
            }
            layoutParams.height = targetHeight
            layoutParams.topMargin = -topExtension
            itemView.layoutParams = layoutParams
        }
    }

    private companion object {
        const val HEADER_ID = Long.MIN_VALUE
    }
}
