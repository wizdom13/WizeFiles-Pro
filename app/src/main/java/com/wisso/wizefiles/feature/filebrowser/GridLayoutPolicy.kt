package com.wisso.wizefiles.feature.filebrowser

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class GridWidthClass {
    COMPACT,
    WIDE
}

@Parcelize
data class GridColumnOverrides(
    val compact: Int = AUTO,
    val wide: Int = AUTO
) : Parcelable {
    fun valueFor(widthClass: GridWidthClass): Int =
        when (widthClass) {
            GridWidthClass.COMPACT -> compact
            GridWidthClass.WIDE -> wide
        }.normalizeGridColumnOverride()

    fun withValue(widthClass: GridWidthClass, value: Int): GridColumnOverrides {
        val normalized = value.normalizeGridColumnOverride()
        return when (widthClass) {
            GridWidthClass.COMPACT -> copy(compact = normalized)
            GridWidthClass.WIDE -> copy(wide = normalized)
        }
    }

    companion object {
        const val AUTO = 0
        val MANUAL_RANGE = 2..6
    }
}

private fun Int.normalizeGridColumnOverride(): Int =
    if (this in GridColumnOverrides.MANUAL_RANGE) this else GridColumnOverrides.AUTO

object GridLayoutPolicy {
    private const val COMPACT_WIDTH_MAX_DP = 599
    private const val THREE_COLUMN_MIN_WIDTH_DP = 360
    private const val FOUR_COLUMN_MIN_WIDTH_DP = 400
    private const val FIVE_COLUMN_MIN_WIDTH_DP = 600
    private const val SIX_COLUMN_MIN_WIDTH_DP = 720

    fun widthClass(availableWidthDp: Int): GridWidthClass =
        if (availableWidthDp <= COMPACT_WIDTH_MAX_DP) {
            GridWidthClass.COMPACT
        } else {
            GridWidthClass.WIDE
        }

    fun automaticSpanCount(availableWidthDp: Int): Int {
        val widthDp = availableWidthDp.coerceAtLeast(0)
        return when {
            widthDp < THREE_COLUMN_MIN_WIDTH_DP -> 2
            widthDp < FOUR_COLUMN_MIN_WIDTH_DP -> 3
            widthDp < FIVE_COLUMN_MIN_WIDTH_DP -> 4
            widthDp < SIX_COLUMN_MIN_WIDTH_DP -> 5
            else -> 6
        }
    }

    fun spanCount(
        availableWidthDp: Int,
        overrides: GridColumnOverrides
    ): Int {
        val override = overrides.valueFor(widthClass(availableWidthDp))
        return if (override in GridColumnOverrides.MANUAL_RANGE) {
            override
        } else {
            automaticSpanCount(availableWidthDp)
        }
    }
}
