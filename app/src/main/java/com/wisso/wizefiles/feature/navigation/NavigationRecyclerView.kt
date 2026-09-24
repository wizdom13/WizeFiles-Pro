package com.wisso.wizefiles.navigation

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.core.graphics.Insets
import androidx.core.graphics.withSave
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.wisso.wizefiles.R
import com.wisso.wizefiles.util.displayWidth
import com.wisso.wizefiles.util.getColorByAttr
import com.wisso.wizefiles.util.getDimensionPixelSize
import com.wisso.wizefiles.util.getDimensionPixelSizeByAttr
import com.wisso.wizefiles.util.isLayoutDirectionRtl

class NavigationRecyclerView : RecyclerView {
    private val verticalPadding = context.getDimensionPixelSize(
        com.google.android.material.R.dimen.design_navigation_padding_bottom
    )
    private val actionBarSize =
        context.getDimensionPixelSizeByAttr(androidx.appcompat.R.attr.actionBarSize)
    private val maxWidth = context.getDimensionPixelSize(R.dimen.navigation_max_width)
    private val scrim = ColorDrawable(
        context.getColorByAttr(com.google.android.material.R.attr.colorPrimaryContainer)
    )

    private var insetStart = 0
    private var insetTop = 0

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr)

    init {
        updatePadding(top = verticalPadding, bottom = verticalPadding)
        fitsSystemWindows = true
        setWillNotDraw(false)
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        var widthSpec = widthSpec
        var width = (context.displayWidth - actionBarSize).coerceIn(0..insetStart + maxWidth)
        when (MeasureSpec.getMode(widthSpec)) {
            MeasureSpec.AT_MOST -> {
                width = width.coerceAtMost(MeasureSpec.getSize(widthSpec))
                widthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
            }
            MeasureSpec.UNSPECIFIED ->
                widthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
            MeasureSpec.EXACTLY -> {}
        }
        super.onMeasure(widthSpec, heightSpec)
    }

    override fun onApplyWindowInsets(insets: android.view.WindowInsets): android.view.WindowInsets {
        val insetsCompat = WindowInsetsCompat.toWindowInsetsCompat(insets, this)
        val systemBarsInsets = insetsCompat.getInsets(WindowInsetsCompat.Type.systemBars())
        val isLayoutDirectionRtl = isLayoutDirectionRtl
        insetStart = if (isLayoutDirectionRtl) {
            systemBarsInsets.right
        } else {
            systemBarsInsets.left
        }
        val paddingLeft = if (isLayoutDirectionRtl) 0 else insetStart
        val paddingRight = if (isLayoutDirectionRtl) insetStart else 0
        insetTop = systemBarsInsets.top
        setPadding(
            paddingLeft,
            verticalPadding + insetTop,
            paddingRight,
            verticalPadding + systemBarsInsets.bottom
        )
        requestLayout()
        val childInsets = Insets.of(
            systemBarsInsets.left - paddingLeft,
            0,
            systemBarsInsets.right - paddingRight,
            0
        )
        return WindowInsetsCompat.Builder(insetsCompat)
            .setInsets(WindowInsetsCompat.Type.systemBars(), childInsets)
            .build()
            .toWindowInsets() ?: insets
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        if (insetTop > 0 && !isHeaderCoveringStatusBar()) {
            canvas.withSave {
                canvas.translate(scrollX.toFloat(), scrollY.toFloat())
                scrim.setBounds(0, 0, width, insetTop)
                scrim.draw(canvas)
            }
        }
    }

    private fun isHeaderCoveringStatusBar(): Boolean {
        val header = findViewHolderForAdapterPosition(0)?.itemView ?: return false
        return header.top < insetTop && header.bottom > insetTop
    }
}
