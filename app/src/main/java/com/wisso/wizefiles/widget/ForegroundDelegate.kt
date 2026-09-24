package com.wisso.wizefiles.widget

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import androidx.core.graphics.drawable.DrawableCompat

/**
 * Small reusable foreground implementation for custom views that don't have native foreground support.
 */
internal class ForegroundDelegate(private val view: View) {
    private var foreground: Drawable? = null
    private var foregroundGravity = Gravity.FILL
    private var foregroundTintList: ColorStateList? = null
    private var foregroundTintMode: PorterDuff.Mode? = null
    private val selfBounds = Rect()
    private val overlayBounds = Rect()
    private var boundsChanged = false

    fun loadFromAttributes(attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) {
        val typedArray = view.context.obtainStyledAttributes(
            attrs,
            intArrayOf(
                android.R.attr.foreground,
                android.R.attr.foregroundGravity,
                android.R.attr.foregroundTint,
                android.R.attr.foregroundTintMode
            ),
            defStyleAttr,
            defStyleRes
        )
        foregroundGravity = typedArray.getInt(1, foregroundGravity)
        if (!typedArray.hasValue(1)) {
            foregroundGravity = Gravity.FILL
        }
        setForeground(typedArray.getDrawable(0))
        if (typedArray.hasValue(2)) {
            foregroundTintList = typedArray.getColorStateList(2)
        }
        if (typedArray.hasValue(3)) {
            foregroundTintMode = parseTintMode(typedArray.getInt(3, -1), foregroundTintMode)
        }
        typedArray.recycle()
        applyTint()
    }

    fun getForeground(): Drawable? = foreground

    fun setForeground(drawable: Drawable?) {
        if (foreground === drawable) {
            return
        }
        foreground?.callback = null
        foreground = drawable?.mutate()?.also {
            it.callback = view
            if (it.isStateful) {
                it.state = view.drawableState
            }
            it.layoutDirection = view.layoutDirection
        }
        applyTint()
        boundsChanged = true
        view.requestLayout()
        view.invalidate()
    }

    fun getForegroundGravity(): Int = foregroundGravity

    fun setForegroundGravity(gravity: Int) {
        if (foregroundGravity == gravity) {
            return
        }
        foregroundGravity = gravity
        boundsChanged = true
        view.requestLayout()
        view.invalidate()
    }

    fun getForegroundTintList(): ColorStateList? = foregroundTintList

    fun setForegroundTintList(tint: ColorStateList?) {
        foregroundTintList = tint
        applyTint()
        view.invalidate()
    }

    fun getForegroundTintMode(): PorterDuff.Mode? = foregroundTintMode

    fun setForegroundTintMode(mode: PorterDuff.Mode?) {
        foregroundTintMode = mode
        applyTint()
        view.invalidate()
    }

    fun draw(canvas: Canvas) {
        val drawable = foreground ?: return
        if (boundsChanged) {
            boundsChanged = false
            selfBounds.set(0, 0, view.width, view.height)
            Gravity.apply(foregroundGravity, drawable.intrinsicWidth, drawable.intrinsicHeight, selfBounds, overlayBounds)
            drawable.bounds = overlayBounds
        }
        drawable.draw(canvas)
    }

    fun drawableStateChanged() {
        foreground?.let {
            if (it.isStateful) {
                it.state = view.drawableState
            }
        }
    }

    fun jumpDrawablesToCurrentState() {
        foreground?.jumpToCurrentState()
    }

    fun verifyDrawable(who: Drawable): Boolean = who === foreground

    fun drawableHotspotChanged(x: Float, y: Float) {
        foreground?.setHotspot(x, y)
    }

    fun onSizeChanged() {
        boundsChanged = true
    }

    private fun applyTint() {
        val drawable = foreground ?: return
        DrawableCompat.setTintList(drawable, foregroundTintList)
        DrawableCompat.setTintMode(drawable, foregroundTintMode ?: PorterDuff.Mode.SRC_IN)
    }

    private fun parseTintMode(value: Int, defaultMode: PorterDuff.Mode?): PorterDuff.Mode? = when (value) {
        3 -> PorterDuff.Mode.SRC_OVER
        5 -> PorterDuff.Mode.SRC_IN
        9 -> PorterDuff.Mode.SRC_ATOP
        14 -> PorterDuff.Mode.MULTIPLY
        15 -> PorterDuff.Mode.SCREEN
        16 -> PorterDuff.Mode.ADD
        else -> defaultMode
    }
}
