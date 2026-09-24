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
