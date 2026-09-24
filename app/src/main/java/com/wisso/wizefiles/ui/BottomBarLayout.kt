package com.wisso.wizefiles.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.core.content.res.use
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.MaterialShapeUtils
import com.google.android.material.shape.ShapeAppearanceModel
import com.wisso.wizefiles.R

class BottomBarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {
    private val style = context.obtainStyledAttributes(
        attrs, R.styleable.BottomBarLayout, defStyleAttr, defStyleRes
    ).use {
        Style(
            it.getDimension(R.styleable.BottomBarLayout_barCornerRadius, 0f),
            it.getBoolean(R.styleable.BottomBarLayout_bottomInsetAsMargin, false)
        )
    }

    private var baseBottomMargin: Int? = null

    init {
        val background = background
        if (background is ColorDrawable) {
            this.background = MaterialShapeDrawable(
                ShapeAppearanceModel.builder()
                    .setAllCornerSizes(style.cornerRadius)
                    .build()
            ).apply {
                fillColor = ColorStateList.valueOf(background.color)
                initializeElevationOverlay(context)
                elevation = this@BottomBarLayout.elevation
            }
        }
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (!style.bottomInsetAsMargin) {
            return super.onApplyWindowInsets(insets)
        }
        val layoutParams = layoutParams as? ViewGroup.MarginLayoutParams ?: return insets
        val baseBottomMargin = baseBottomMargin
            ?: layoutParams.bottomMargin.also { this.baseBottomMargin = it }
        val windowInsets = WindowInsetsCompat.toWindowInsetsCompat(insets, this)
        val systemBarsBottomInset = windowInsets
            .getInsets(WindowInsetsCompat.Type.systemBars())
            .bottom
        val imeBottomInset = windowInsets
            .getInsets(WindowInsetsCompat.Type.ime())
            .bottom
        val resolvedBottomMargin = resolveBottomBarMargin(
            baseBottomMargin,
            systemBarsBottomInset,
            imeBottomInset
        )
        if (layoutParams.bottomMargin != resolvedBottomMargin) {
            layoutParams.bottomMargin = resolvedBottomMargin
            requestLayout()
        }
        return insets
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        MaterialShapeUtils.setParentAbsoluteElevation(this)
    }

    override fun setElevation(elevation: Float) {
        super.setElevation(elevation)

        MaterialShapeUtils.setElevation(this, elevation)
    }

    private data class Style(
        val cornerRadius: Float,
        val bottomInsetAsMargin: Boolean
    )
}

internal fun resolveBottomBarMargin(
    baseBottomMargin: Int,
    systemBarsBottomInset: Int,
    imeBottomInset: Int
): Int = baseBottomMargin + maxOf(systemBarsBottomInset, imeBottomInset)
