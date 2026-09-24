// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.imageloader.coil

import android.graphics.Bitmap
import android.os.Build
import coil.decode.DataSource
import coil.size.Dimension
import coil.size.Scale
import coil.size.Size
import coil.size.isOriginal
import coil.size.pxOrElse
import com.wisso.wizefiles.feature.filebrowser.isRemotePath
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull

val Bitmap.Config.isHardware: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && this == Bitmap.Config.HARDWARE

fun Bitmap.Config.toSoftware(): Bitmap.Config = if (isHardware) Bitmap.Config.ARGB_8888 else this

val AppPath.dataSource: DataSource
    get() = if (toLegacyPathOrNull()?.isRemotePath == true) DataSource.NETWORK else DataSource.DISK

inline fun Size.widthPx(scale: Scale, original: () -> Int): Int =
    if (isOriginal) original() else width.toPx(scale)

inline fun Size.heightPx(scale: Scale, original: () -> Int): Int =
    if (isOriginal) original() else height.toPx(scale)

fun Dimension.toPx(scale: Scale) =
    pxOrElse {
        when (scale) {
            Scale.FILL -> Int.MIN_VALUE
            Scale.FIT -> Int.MAX_VALUE
        }
    }
