package com.wisso.wizefiles.util

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.wisso.wizefiles.core.app.inputMethodManager

fun View.doOnGlobalLayout(block: () -> Unit): OneShotGlobalLayoutListener =
    OneShotGlobalLayoutListener.add(this, block)

/** @see androidx.core.view.OneShotPreDrawListener */
class OneShotGlobalLayoutListener private constructor(
    private val view: View,
    private val block: () -> Unit
) : ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
    private var viewTreeObserver = view.viewTreeObserver

    override fun onPreDraw(): Boolean {
        removeListener()
        block()
        return true
    }

    override fun onViewAttachedToWindow(view: View) {
        viewTreeObserver = view.viewTreeObserver
    }

    override fun onViewDetachedFromWindow(view: View) {
        removeListener()
    }

    fun removeListener() {
        if (viewTreeObserver.isAlive) {
            viewTreeObserver.removeOnPreDrawListener(this)
        } else {
            view.viewTreeObserver.removeOnPreDrawListener(this)
        }
        view.removeOnAttachStateChangeListener(this)
    }

    companion object {
        fun add(view: View, block: () -> Unit): OneShotGlobalLayoutListener =
            OneShotGlobalLayoutListener(view, block).also {
                view.viewTreeObserver.addOnPreDrawListener(it)
                view.addOnAttachStateChangeListener(it)
            }
    }
}

inline fun <reified T : View> View.findViewByClass(): T? = findViewByClass(T::class.java)

fun <T : View> View.findViewByClass(clazz: Class<T>): T? {
    if (clazz.isInstance(this)) {
        @Suppress("UNCHECKED_CAST")
        return this as T
    }
    if (this is ViewGroup) {
        children.forEach {
            it.findViewByClass(clazz)?.let { return it }
        }
    }
    return null
}

val View.isLayoutDirectionRtl: Boolean
    get() = layoutDirection == View.LAYOUT_DIRECTION_RTL

var View.layoutInStatusBar: Boolean
    get() = false
    set(value) {
        if (value) {
            context.activity?.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
            }
        }
    }

var View.layoutInNavigation: Boolean
    get() = false
    set(value) {
        if (value) {
            context.activity?.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
            }
        }
    }

fun View.applyInsetPadding(
    applyLeft: Boolean = false,
    applyTop: Boolean = false,
    applyRight: Boolean = false,
    applyBottom: Boolean = false,
    applyImeBottom: Boolean = false
) {
    val initialLeft = paddingLeft
    val initialTop = paddingTop
    val initialRight = paddingRight
    val initialBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
        val bottomInset = if (applyImeBottom) {
            maxOf(systemBarsInsets.bottom, imeInsets.bottom)
        } else {
            systemBarsInsets.bottom
        }
        view.updatePadding(
            left = initialLeft + if (applyLeft) systemBarsInsets.left else 0,
            top = initialTop + if (applyTop) systemBarsInsets.top else 0,
            right = initialRight + if (applyRight) systemBarsInsets.right else 0,
            bottom = initialBottom + if (applyBottom) bottomInset else 0
        )
        insets
    }
    if (isAttachedToWindow) {
        requestApplyInsets()
    } else {
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                view.removeOnAttachStateChangeListener(this)
                view.requestApplyInsets()
            }

            override fun onViewDetachedFromWindow(view: View) = Unit
        })
    }
}

fun View.dispatchSystemBarInsetsCompat(
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    baseInsets: WindowInsetsCompat
): WindowInsetsCompat {
    val systemBarsInsets = Insets.of(left, top, right, bottom)
    val builder = WindowInsetsCompat.Builder(baseInsets)
        .setInsets(WindowInsetsCompat.Type.systemBars(), systemBarsInsets)
    if (baseInsets.isVisible(WindowInsetsCompat.Type.statusBars())) {
        builder.setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(left, top, right, 0))
    }
    if (baseInsets.isVisible(WindowInsetsCompat.Type.navigationBars())) {
        builder.setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(left, 0, right, bottom))
    }
    return builder.build()
}

suspend fun View.fadeIn(force: Boolean = false) {
    if (!isVisible) {
        alpha = 0f
        isVisible = true
    }
    animate().run {
        alpha(1f)
        if (!(isLaidOut || force) || (isVisible && alpha == 1f)) {
            duration = 0
        } else {
            duration = context.shortAnimTime.toLong()
            interpolator = context.getInterpolator(android.R.interpolator.fast_out_slow_in)
        }
        start()
        awaitEnd()
    }
}

fun View.fadeInUnsafe(force: Boolean = false) {
    if (!isVisible) {
        alpha = 0f
        isVisible = true
    }
    animate().run {
        alpha(1f)
        if (!(isLaidOut || force) || (isVisible && alpha == 1f)) {
            duration = 0
        } else {
            duration = context.shortAnimTime.toLong()
            interpolator = context.getInterpolator(android.R.interpolator.fast_out_slow_in)
        }
        start()
    }
}

suspend fun View.fadeOut(force: Boolean = false, gone: Boolean = false) {
    animate().run {
        alpha(0f)
        if (!(isLaidOut || force) || (!isVisible || alpha == 0f)) {
            duration = 0
        } else {
            duration = context.shortAnimTime.toLong()
            interpolator = context.getInterpolator(android.R.interpolator.fast_out_linear_in)
        }
        start()
        awaitEnd()
    }
    if (gone) {
        isGone = true
    } else {
        isInvisible = true
    }
}

fun View.fadeOutUnsafe(force: Boolean = false, gone: Boolean = false) {
    animate().run {
        alpha(0f)
        if (!(isLaidOut || force) || (!isVisible || alpha == 0f)) {
            duration = 0
        } else {
            duration = context.shortAnimTime.toLong()
            interpolator = context.getInterpolator(android.R.interpolator.fast_out_linear_in)
        }
        withEndAction {
            if (gone) {
                isGone = true
            } else {
                isInvisible = true
            }
        }
        start()
    }
}

suspend fun View.fadeToVisibility(visible: Boolean, force: Boolean = false, gone: Boolean = false) {
    if (visible) {
        fadeIn(force)
    } else {
        fadeOut(force, gone)
    }
}

fun View.fadeToVisibilityUnsafe(visible: Boolean, force: Boolean = false, gone: Boolean = false) {
    if (visible) {
        fadeInUnsafe(force)
    } else {
        fadeOutUnsafe(force, gone)
    }
}

fun View.showSoftInput() {
    inputMethodManager.showSoftInput(this, 0)
}

fun View.hideSoftInput() {
    inputMethodManager.hideSoftInputFromWindow(windowToken, 0)
}
