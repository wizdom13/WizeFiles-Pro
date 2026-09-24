package com.wisso.wizefiles.util

import android.view.Window
import android.view.WindowManager

fun Window.showImeWithResize() {
    setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or softInputAdjustResizeCompat())
}

@Suppress("DEPRECATION")
private fun softInputAdjustResizeCompat(): Int = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
