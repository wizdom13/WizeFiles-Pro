// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.fontviewer

import kotlin.math.roundToInt

/** Keeps the font preview slider on its logical SP grid without converting back from pixels. */
internal object FontPreviewSize {
    const val MIN_SP = 12f
    const val MAX_SP = 84f
    const val STEP_SP = 1f
    const val DEFAULT_SP = 36f

    fun normalize(valueSp: Float?): Float {
        val finiteValue = valueSp?.takeIf { it.isFinite() } ?: DEFAULT_SP
        return finiteValue
            .roundToInt()
            .toFloat()
            .coerceIn(MIN_SP, MAX_SP)
    }
}
