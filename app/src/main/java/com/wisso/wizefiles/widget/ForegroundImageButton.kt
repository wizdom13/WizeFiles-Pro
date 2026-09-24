// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.appcompat.widget.AppCompatImageButton

class ForegroundImageButton : AppCompatImageButton, ForegroundViewSupport {
    private val foregroundDelegate = ForegroundDelegate(this)

    constructor(context: Context) : super(context) {
        foregroundDelegate.loadFromAttributes(attrs = null, defStyleAttr = 0, defStyleRes = 0)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        foregroundDelegate.loadFromAttributes(attrs, defStyleAttr = 0, defStyleRes = 0)
    }

    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        foregroundDelegate.loadFromAttributes(attrs, defStyleAttr, defStyleRes = 0)
    }

    constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int,
        @StyleRes defStyleRes: Int
    ) : super(context, attrs, defStyleAttr) {
        // AppCompatImageButton has no defStyleRes constructor; parse our attrs with defStyleRes anyway.
        foregroundDelegate.loadFromAttributes(attrs, defStyleAttr, defStyleRes)
    }

    override var foregroundCompatDrawable: Drawable?
        get() = foregroundDelegate.getForeground()
        set(value) {
            foregroundDelegate.setForeground(value)
        }

    override var foregroundCompatGravity: Int
        get() = foregroundDelegate.getForegroundGravity()
        set(value) {
            foregroundDelegate.setForegroundGravity(value)
        }

    override var foregroundCompatTintList: ColorStateList?
        get() = foregroundDelegate.getForegroundTintList()
        set(value) {
            foregroundDelegate.setForegroundTintList(value)
        }

    override var foregroundCompatTintMode: PorterDuff.Mode?
        get() = foregroundDelegate.getForegroundTintMode()
        set(value) {
            foregroundDelegate.setForegroundTintMode(value)
        }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        foregroundDelegate.draw(canvas)
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        foregroundDelegate.drawableStateChanged()
    }

    override fun jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState()
        foregroundDelegate.jumpDrawablesToCurrentState()
    }

    override fun verifyDrawable(who: Drawable): Boolean =
        super.verifyDrawable(who) || foregroundDelegate.verifyDrawable(who)

    override fun drawableHotspotChanged(x: Float, y: Float) {
        super.drawableHotspotChanged(x, y)
        foregroundDelegate.drawableHotspotChanged(x, y)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        foregroundDelegate.onSizeChanged()
    }
}
