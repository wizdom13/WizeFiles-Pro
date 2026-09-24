// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.widget

import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable

interface ForegroundViewSupport {
    var foregroundCompatDrawable: Drawable?
    var foregroundCompatGravity: Int
    var foregroundCompatTintList: ColorStateList?
    var foregroundCompatTintMode: PorterDuff.Mode?
}
