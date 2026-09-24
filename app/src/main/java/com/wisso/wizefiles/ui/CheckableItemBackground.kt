// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.annotation.Dimension
import androidx.appcompat.graphics.drawable.AnimatedStateListDrawableCompat
import com.wisso.wizefiles.util.dpToDimension
import com.wisso.wizefiles.util.dpToDimensionPixelOffset
import com.wisso.wizefiles.util.getColorByAttr
import com.wisso.wizefiles.util.shortAnimTime

object CheckableItemBackground {
    // Build this programmatically because the selected container color is a theme attribute and
    // the background also needs the grid-specific inset and corner radius.
    @SuppressLint("RestrictedApi")
    fun create(
        @Dimension(unit = Dimension.DP) insetDp: Float,
        @Dimension(unit = Dimension.DP) cornerSizeDp: Float,
        context: Context
    ): Drawable =
        AnimatedStateListDrawableCompat().apply {
            val shortAnimTime = context.shortAnimTime
            setEnterFadeDuration(shortAnimTime)
            setExitFadeDuration(shortAnimTime)
            val checkedDrawable = GradientDrawable().apply {
                cornerRadius = context.dpToDimension(cornerSizeDp)
                setColor(
                    context.getColorByAttr(
                        com.google.android.material.R.attr.colorSecondaryContainer
                    )
                )
                setStroke(2 * context.dpToDimensionPixelOffset(insetDp), Color.TRANSPARENT)
            }
            addState(intArrayOf(android.R.attr.state_checked), checkedDrawable)
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }
}
