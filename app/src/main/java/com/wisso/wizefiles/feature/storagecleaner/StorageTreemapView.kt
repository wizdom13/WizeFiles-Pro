// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storagecleaner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.text.format.Formatter
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.min

class StorageTreemapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Entry(
        val category: StorageCompositionCategory,
        val label: String,
        val bytes: Long,
        val itemCount: Int,
        val color: Int
    )

    private data class RenderedEntry(
        val entry: Entry,
        val bounds: RectF
    )

    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 16.sp
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val sizePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13.sp
    }
    private val gap = 2.dp
    private val cornerRadius = 12.dp
    private val textPadding = 10.dp
    private var entries: List<Entry> = emptyList()
    private var renderedEntries: List<RenderedEntry> = emptyList()

    var onEntryClick: ((Entry) -> Unit)? = null

    init {
        isClickable = true
    }

    fun setEntries(entries: List<Entry>) {
        this.entries = entries.filter { it.bytes > 0L }.sortedByDescending { it.bytes }
        contentDescription = this.entries.joinToString(separator = ", ") {
            "${it.label}, ${Formatter.formatFileSize(context, it.bytes)}"
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0 || entries.isEmpty()) {
            renderedEntries = emptyList()
            return
        }

        val contentWidth = (width - paddingLeft - paddingRight).coerceAtLeast(0)
        val contentHeight = (height - paddingTop - paddingBottom).coerceAtLeast(0)
        if (contentWidth == 0 || contentHeight == 0) return

        val layout = buildTreemapLayout(entries.map { TreemapWeight(it, it.bytes) })
        renderedEntries = layout.map { cell ->
            val bounds = RectF(
                paddingLeft + cell.left * contentWidth,
                paddingTop + cell.top * contentHeight,
                paddingLeft + cell.right * contentWidth,
                paddingTop + cell.bottom * contentHeight
            )
            bounds.inset(min(gap, bounds.width() * 0.15f), min(gap, bounds.height() * 0.15f))
            drawEntry(canvas, cell.value, bounds)
            RenderedEntry(cell.value, bounds)
        }
    }

    private fun drawEntry(canvas: Canvas, entry: Entry, bounds: RectF) {
        tilePaint.color = entry.color
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, tilePaint)

        if (bounds.width() < 68.dp || bounds.height() < 48.dp) return

        val textColor = if (ColorUtils.calculateLuminance(entry.color) > 0.48) {
            android.graphics.Color.BLACK
        } else {
            android.graphics.Color.WHITE
        }
        titlePaint.color = textColor
        sizePaint.color = textColor

        val availableWidth = (bounds.width() - textPadding * 2).coerceAtLeast(0f)
        val title = TextUtils.ellipsize(entry.label, titlePaint, availableWidth, TextUtils.TruncateAt.END)
        val titleY = bounds.top + textPadding - titlePaint.fontMetrics.top
        canvas.drawText(title, 0, title.length, bounds.left + textPadding, titleY, titlePaint)

        if (bounds.height() >= 76.dp) {
            val size = Formatter.formatFileSize(context, entry.bytes)
            val sizeY = titleY + titlePaint.fontSpacing + 4.dp
            canvas.drawText(size, bounds.left + textPadding, sizeY, sizePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            renderedEntries.firstOrNull { it.bounds.contains(event.x, event.y) }?.let {
                onEntryClick?.invoke(it.entry)
                performClick()
            }
        }
        return event.action == MotionEvent.ACTION_DOWN || super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private val Int.dp: Float
        get() = this * resources.displayMetrics.density

    private val Int.sp: Float
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            toFloat(),
            resources.displayMetrics
        )
}
