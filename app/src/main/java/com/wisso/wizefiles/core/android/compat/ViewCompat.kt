package com.wisso.wizefiles.core.android.compat

import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.IdRes
import androidx.core.view.ViewCompat
import com.wisso.wizefiles.widget.ForegroundViewSupport

@Suppress("UNCHECKED_CAST")
fun <T : View> View.requireViewByIdCompat(@IdRes id: Int): T =
    ViewCompat.requireViewById(this, id) as T

var View.scrollIndicatorsCompat: Int
    get() = ViewCompat.getScrollIndicators(this)
    set(value) {
        ViewCompat.setScrollIndicators(this, value)
    }

var View.foregroundCompat: Drawable?
    get() = when (this) {
        is ForegroundViewSupport -> foregroundCompatDrawable
        is FrameLayout -> foreground
        else -> null
    }
    set(value) {
        when (this) {
            is ForegroundViewSupport -> foregroundCompatDrawable = value
            is FrameLayout -> foreground = value
        }
    }

var View.foregroundGravityCompat: Int
    get() = when (this) {
        is ForegroundViewSupport -> foregroundCompatGravity
        is FrameLayout -> foregroundGravity
        else -> 0
    }
    set(value) {
        when (this) {
            is ForegroundViewSupport -> foregroundCompatGravity = value
            is FrameLayout -> foregroundGravity = value
        }
    }

var View.foregroundTintListCompat: ColorStateList?
    get() = when (this) {
        is ForegroundViewSupport -> foregroundCompatTintList
        is FrameLayout -> foregroundTintList
        else -> null
    }
    set(value) {
        when (this) {
            is ForegroundViewSupport -> foregroundCompatTintList = value
            is FrameLayout -> foregroundTintList = value
        }
    }

var View.foregroundTintModeCompat: PorterDuff.Mode?
    get() = when (this) {
        is ForegroundViewSupport -> foregroundCompatTintMode
        is FrameLayout -> foregroundTintMode
        else -> null
    }
    set(value) {
        when (this) {
            is ForegroundViewSupport -> foregroundCompatTintMode = value
            is FrameLayout -> foregroundTintMode = value
        }
    }
