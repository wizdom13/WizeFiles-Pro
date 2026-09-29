// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.ui

import android.graphics.Rect
import android.view.View
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
        // We own the padding policy. The default fitsSystemWindows handler would
        // replace it with only the system insets, losing the space for floating bars.
        return insets
    }
}
