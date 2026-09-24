// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.imageloader.coil

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.os.Process
import androidx.core.graphics.drawable.toDrawable
import com.wisso.wizefiles.core.android.compat.longVersionCodeCompat

class AppIconCompatLoader(
    private val context: Context,
    private val iconSize: Int,
    private val shrinkNonAdaptiveIcons: Boolean = false,
    private val badged: Boolean = false
) {
    private val packageManager: PackageManager = context.packageManager

    fun loadIcon(applicationInfo: ApplicationInfo): Drawable {
        val icon = packageManager.getApplicationIcon(applicationInfo)
        val maybeShrunk = if (shrinkNonAdaptiveIcons && icon !is AdaptiveIconDrawable) {
            InsetDrawable(icon, iconSize / 8)
        } else {
            icon
        }
        val maybeBadged = if (badged) {
            packageManager.getUserBadgedIcon(maybeShrunk, Process.myUserHandle())
        } else {
            maybeShrunk
        }
        return maybeBadged.renderToIconSize(context, iconSize)
    }

    companion object {
        fun getIconKey(data: ApplicationInfo): String = buildString {
            append(data.packageName)
            append(':')
            append(data.longVersionCodeCompat)
            append(':')
            append(data.sourceDir)
            append(':')
            append(data.publicSourceDir)
            append(':')
            append(data.icon)
        }
    }
}

private fun Drawable.renderToIconSize(context: Context, iconSize: Int): Drawable {
    if (this is BitmapDrawable && bitmap.width == iconSize && bitmap.height == iconSize) {
        return this
    }
    val bitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val oldBounds = bounds
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    setBounds(oldBounds)
    return bitmap.toDrawable(context.resources)
}
