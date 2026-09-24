// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.ui

import android.content.Context
import android.util.AttributeSet
import android.view.WindowInsets
import androidx.annotation.AttrRes
import com.drakeet.drawer.FullDraggableContainer

class IgnoreFitsSystemWindowsFullyDraggableDrawerContentLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, @AttrRes defStyleAttr: Int = 0
) : FullDraggableContainer(context, attrs, defStyleAttr) {
    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets = insets
}
