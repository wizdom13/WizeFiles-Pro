// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.ui

import androidx.recyclerview.widget.AdapterListUpdateCallback
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

abstract class ListAdapter<T, VH : RecyclerView.ViewHolder>(
    itemCallback: DiffUtil.ItemCallback<T>
) : RecyclerView.Adapter<VH>() {
    private val differ = ListDiffer(AdapterListUpdateCallback(this), itemCallback)

    val list: List<T>
        get() = differ.list

    final override fun getItemId(position: Int): Long = RecyclerView.NO_ID
    final override fun getItemCount(): Int = differ.list.size

    fun getItem(position: Int): T = differ.list[position]

    open fun refresh() {
        val snapshot = differ.list
        differ.list = emptyList()
        differ.list = snapshot
    }

    open fun replace(list: List<T>, clear: Boolean) {
        if (clear) differ.list = emptyList()
        differ.list = list
    }

    open fun clear() {
        differ.list = emptyList()
    }
}
