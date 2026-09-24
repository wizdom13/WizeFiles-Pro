// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storagecleaner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.wisso.wizefiles.R

class StorageCategoryCompositionBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = Path()
    private val rect = RectF()
    private var segments: List<CompositionSegment> = emptyList()
    private val remainderColor: Int by lazy { ContextCompat.getColor(context, R.color.storage_category_composition_remainder) }

    fun setSegments(newSegments: List<CompositionSegment>) {
        segments = newSegments.filter { it.fraction > 0f }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (segments.isEmpty() || width <= 0 || height <= 0) return

        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        clipPath.reset()
        val cornerRadius = height / 2f
        clipPath.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)

        canvas.save()
        canvas.clipPath(clipPath)

        paint.color = remainderColor
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        var left = 0f
        segments.forEach { segment ->
            if (left >= width) return@forEach
            val segmentWidth = (width * segment.fraction).coerceAtLeast(0f)
            if (segmentWidth <= 0f) return@forEach
            val right = (left + segmentWidth).coerceAtMost(width.toFloat())
            paint.color = segment.color
            canvas.drawRect(left, 0f, right, height.toFloat(), paint)
            left = right
        }

        canvas.restore()
    }

    data class CompositionSegment(
        @ColorInt val color: Int,
        val fraction: Float
    )
}
