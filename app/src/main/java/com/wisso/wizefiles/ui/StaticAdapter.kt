// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.ui

import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import com.wisso.wizefiles.util.layoutInflater

class StaticAdapter(
    @LayoutRes val layoutRes: Int,
    val listener: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<StaticAdapter.ViewHolder>() {
    @get:JvmName("_getItemCount")
    var itemCount: Int = 1
        set(value) {
            require(value >= 0) { "Item count must not be negative" }
            if (field == value) return
            val previous = field
            field = value
            if (value < previous) {
                notifyItemRangeRemoved(value, previous - value)
            } else {
                notifyItemRangeInserted(previous, value - previous)
            }
        }

    init {
        setHasStableIds(true)
    }

    override fun getItemCount(): Int = itemCount
    override fun getItemId(position: Int): Long = position.toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = parent.context.layoutInflater.inflate(layoutRes, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.itemView.setOnClickListener(
            listener?.let { action ->
                View.OnClickListener {
                    val current = holder.bindingAdapterPosition
                    if (current != RecyclerView.NO_POSITION) action(current)
                }
            }
        )
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
