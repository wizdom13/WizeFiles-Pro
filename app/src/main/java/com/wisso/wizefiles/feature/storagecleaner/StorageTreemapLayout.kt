// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storagecleaner

internal data class TreemapWeight<T>(
    val value: T,
    val weight: Long
)

internal data class TreemapRect<T>(
    val value: T,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val area: Float
        get() = (right - left) * (bottom - top)
}

/**
 * Produces a deterministic binary treemap in normalized 0..1 coordinates.
 * Each split follows the longest edge and balances the weight on both sides.
 */
internal fun <T> buildTreemapLayout(entries: List<TreemapWeight<T>>): List<TreemapRect<T>> {
    val positiveEntries = entries.filter { it.weight > 0L }
    if (positiveEntries.isEmpty()) return emptyList()

    val result = mutableListOf<TreemapRect<T>>()

    fun layout(
        remaining: List<TreemapWeight<T>>,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ) {
        if (remaining.size == 1) {
            result += TreemapRect(remaining.single().value, left, top, right, bottom)
            return
        }

        val totalWeight = remaining.sumOf { it.weight }.toDouble()
        var prefixWeight = 0L
        var splitIndex = 1
        var bestDifference = Double.MAX_VALUE
        for (index in 1 until remaining.size) {
            prefixWeight += remaining[index - 1].weight
            val difference = kotlin.math.abs(totalWeight / 2.0 - prefixWeight.toDouble())
            if (difference < bestDifference) {
                bestDifference = difference
                splitIndex = index
            }
        }

        val first = remaining.subList(0, splitIndex)
        val second = remaining.subList(splitIndex, remaining.size)
        val firstFraction = (first.sumOf { it.weight }.toDouble() / totalWeight)
            .toFloat()
            .coerceIn(0.001f, 0.999f)

        if (right - left >= bottom - top) {
            val split = left + (right - left) * firstFraction
            layout(first, left, top, split, bottom)
            layout(second, split, top, right, bottom)
        } else {
            val split = top + (bottom - top) * firstFraction
            layout(first, left, top, right, split)
            layout(second, left, split, right, bottom)
        }
    }

    layout(positiveEntries, 0f, 0f, 1f, 1f)
    return result
}
