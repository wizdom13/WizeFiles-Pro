package com.wisso.wizefiles.ui

import android.graphics.Rect
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ScrollingViewOnApplyWindowInsetsListener(
    view: View,
    private val onInsetsApplied: ((bottomInset: Int) -> Unit)? = null
) : View.OnApplyWindowInsetsListener {
    private val initialPadding =
        Rect(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)

    override fun onApplyWindowInsets(
        view: View,
        insets: android.view.WindowInsets
    ): android.view.WindowInsets {
        val insetsCompat = WindowInsetsCompat.toWindowInsetsCompat(insets, view)
        val systemBarsInsets = insetsCompat.getInsets(WindowInsetsCompat.Type.systemBars())
        view.setPadding(
            initialPadding.left,
            initialPadding.top,
            initialPadding.right,
            initialPadding.bottom + systemBarsInsets.bottom
        )
        onInsetsApplied?.invoke(systemBarsInsets.bottom)
        return ViewCompat.onApplyWindowInsets(view, insetsCompat).toWindowInsets() ?: insets
    }
}
