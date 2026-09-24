// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.ui

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback

class ListDiffer<T>(
    private val updates: ListUpdateCallback,
    private val items: DiffUtil.ItemCallback<T>
) {
    private var current: List<T> = emptyList()

    var list: List<T>
        get() = current
        set(value) = submit(value)

    private fun submit(incoming: List<T>) {
        if (incoming === current || incoming.isEmpty() && current.isEmpty()) return

        val replacement = incoming.toList()
        val previous = current
        when {
            replacement.isEmpty() -> {
                current = emptyList()
                updates.onRemoved(0, previous.size)
            }
            previous.isEmpty() -> {
                current = replacement
                updates.onInserted(0, replacement.size)
            }
            else -> {
                val diff = DiffUtil.calculateDiff(
                    SnapshotDiff(previous, replacement, items),
                    true
                )
                current = replacement
                diff.dispatchUpdatesTo(updates)
            }
        }
    }

    private class SnapshotDiff<T>(
        private val oldItems: List<T>,
        private val newItems: List<T>,
        private val callback: DiffUtil.ItemCallback<T>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldItems.size
        override fun getNewListSize(): Int = newItems.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val old = oldItems[oldItemPosition]
            val new = newItems[newItemPosition]
            if (old == null || new == null) return old == null && new == null
            return callback.areItemsTheSame(old, new)
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val old = oldItems[oldItemPosition]
            val new = newItems[newItemPosition]
            if (old == null || new == null) return old == null && new == null
            return callback.areContentsTheSame(old, new)
        }

        override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
            val old = oldItems[oldItemPosition]
            val new = newItems[newItemPosition]
            if (old == null || new == null) return null
            return callback.getChangePayload(old, new)
        }

    }
}
