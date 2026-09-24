// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.android.compat

import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.TileService
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.view.doOnPreDraw
import com.wisso.wizefiles.hiddenapi.RestrictedHiddenApi
import com.wisso.wizefiles.util.lazyReflectedField

// Work around https://issuetracker.google.com/issues/299506164 on U which is fixed in V.
fun TileService.doWithStartForegroundServiceAllowed(action: () -> Unit) {
    if (Build.VERSION.SDK_INT != Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        action()
        return
    }
    val windowManager = getSystemService(WindowManager::class.java)
    val view = View(this)
    val layoutParams =
        WindowManager.LayoutParams().apply {
            type = WindowManager_LayoutParams_TYPE_QS_DIALOG
            format = PixelFormat.TRANSLUCENT
            token = this@doWithStartForegroundServiceAllowed.token
        }
    windowManager.addView(view, layoutParams)
    // We need to wait for WindowState.onSurfaceShownChanged(), basically when the first draw has
    // finished and the surface is about to be shown to the user. However there's no good callback
    // for that, while waiting for the second pre-draw seems to work.
    view.doOnPreDraw {
        view.post {
            view.invalidate()
            view.doOnPreDraw {
                try {
                    action()
                } finally {
                    windowManager.removeView(view)
                }
            }
        }
    }
}

private const val WindowManager_LayoutParams_TYPE_QS_DIALOG =
    WindowManager.LayoutParams.FIRST_SYSTEM_WINDOW + 35

@delegate:RequiresApi(Build.VERSION_CODES.N)
@get:RequiresApi(Build.VERSION_CODES.N)
@RestrictedHiddenApi
private val tokenField by lazyReflectedField(TileService::class.qualifiedName!!, "mToken")

private val TileService.token: IBinder?
    @RequiresApi(Build.VERSION_CODES.N)
    get() = tokenField.get(this) as IBinder?
