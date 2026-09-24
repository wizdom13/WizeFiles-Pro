// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.ui

import androidx.recyclerview.widget.RecyclerView

abstract class SimpleAdapter<T, VH : RecyclerView.ViewHolder> : RecyclerView.Adapter<VH>() {
    private val mutableItems = ArrayList<T>()
    val list: List<T>
        get() = mutableItems

    protected abstract val hasStableIds: Boolean

    init {
        setHasStableIds(hasStableIds)
    }

    fun addAll(collection: Collection<T>) {
        if (collection.isEmpty()) return
        val start = mutableItems.size
        mutableItems.addAll(collection)
        notifyItemRangeInserted(start, collection.size)
    }

    fun replace(collection: Collection<T>) {
        mutableItems.clear()
        mutableItems.addAll(collection)
        notifyDataSetChanged()
    }

    fun add(position: Int, item: T) {
        mutableItems.add(position, item)
        notifyItemInserted(position)
    }

    fun add(item: T) = add(mutableItems.size, item)

    operator fun set(position: Int, item: T) {
        mutableItems[position] = item
        notifyItemChanged(position)
    }

    fun remove(position: Int): T {
        val removed = mutableItems.removeAt(position)
        notifyItemRemoved(position)
        return removed
    }

    fun clear() {
        if (mutableItems.isEmpty()) return
        val count = mutableItems.size
        mutableItems.clear()
        notifyItemRangeRemoved(0, count)
    }

    fun findPositionById(id: Long): Int =
        mutableItems.indices.firstOrNull { getItemId(it) == id } ?: RecyclerView.NO_POSITION

    fun notifyItemChangedById(id: Long) {
        findPositionById(id).takeIf { it != RecyclerView.NO_POSITION }?.let(::notifyItemChanged)
    }

    fun removeById(id: Long): T? =
        findPositionById(id).takeIf { it != RecyclerView.NO_POSITION }?.let(::remove)

    fun getItem(position: Int): T = mutableItems[position]

    final override fun getItemCount(): Int = mutableItems.size
}
