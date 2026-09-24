// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.android.compat

import android.app.Activity
import android.app.ActivityManager.TaskDescription
import android.graphics.Color
import android.os.Build
import androidx.annotation.StyleRes
import androidx.core.app.ActivityCompat
import com.wisso.wizefiles.util.getColorByAttr

fun Activity.recreateCompat() {
    ActivityCompat.recreate(this)
}

fun Activity.setThemeCompat(@StyleRes resid: Int) {
    setTheme(resid)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val surfaceColor = getColorByAttr(com.google.android.material.R.attr.colorSurface)
        if (surfaceColor != 0 && Color.alpha(surfaceColor) == 0xFF) {
            @Suppress("DEPRECATION")
            setTaskDescription(TaskDescription(null, null, surfaceColor))
        }
    }
}
