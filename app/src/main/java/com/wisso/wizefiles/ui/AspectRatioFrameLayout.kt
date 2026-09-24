// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.ui

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.android.compat.obtainStyledAttributesCompat
import com.wisso.wizefiles.core.android.compat.use
import kotlin.math.min
import kotlin.math.roundToInt

class AspectRatioFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {
    @SuppressLint("RestrictedApi")
    private val maximumWidth = context.obtainStyledAttributesCompat(
        attrs, R.styleable.AspectRatioFrameLayout, defStyleAttr, defStyleRes
    ).use {
        it.getDimensionPixelSize(R.styleable.AspectRatioFrameLayout_aspectRatioMaxWidth, 0)
    }

    @SuppressLint("RestrictedApi")
    var ratio: Float = context.obtainStyledAttributesCompat(
        attrs, R.styleable.AspectRatioFrameLayout, defStyleAttr, defStyleRes
    ).use { it.getFloat(R.styleable.AspectRatioFrameLayout_aspectRatio, 0f) }
        set(value) {
            if (field == value) {
                return
            }
            field = value
            requestLayout()
            invalidate()
        }

    fun setRatio(width: Float, height: Float) {
        ratio = width / height
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val newWidthMeasureSpec: Int
        val newHeightMeasureSpec: Int
        if (ratio > 0) {
            val widthMode = MeasureSpec.getMode(widthMeasureSpec)
            if (widthMode == MeasureSpec.EXACTLY) {
                val width = constrainWidth(MeasureSpec.getSize(widthMeasureSpec))
                val height = (width / ratio).roundToInt().coerceAtLeast(minimumHeight)
                newWidthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
                newHeightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
            } else {
                val height = MeasureSpec.getSize(heightMeasureSpec)
                val width = constrainWidth(
                    (ratio * height).roundToInt().coerceAtLeast(minimumWidth)
                )
                newWidthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
                newHeightMeasureSpec = heightMeasureSpec
            }
        } else {
            newWidthMeasureSpec = widthMeasureSpec
            newHeightMeasureSpec = heightMeasureSpec
        }
        super.onMeasure(newWidthMeasureSpec, newHeightMeasureSpec)
    }

    private fun constrainWidth(width: Int): Int =
        if (maximumWidth > 0) min(width, maximumWidth) else width
}
