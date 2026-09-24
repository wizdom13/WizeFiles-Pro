// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.android.compat

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.KClass

val KClass<MediaMetadataRetriever>.METADATA_KEY_SAMPLERATE: Int
    @RequiresApi(Build.VERSION_CODES.Q)
    get() = 38

fun MediaMetadataRetriever.getFrameAtTimeCompat(
    timeUs: Long,
    option: Int,
    params: MediaMetadataRetriever.BitmapParams?
): Bitmap? {
    return if (params != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        getFrameAtTime(timeUs, option, params)
    } else {
        getFrameAtTime(timeUs, option)
    }
}

@RequiresApi(Build.VERSION_CODES.O_MR1)
fun MediaMetadataRetriever.getScaledFrameAtTimeCompat(
    timeUs: Long,
    option: Int,
    dstWidth: Int,
    dstHeight: Int,
    params: MediaMetadataRetriever.BitmapParams?
): Bitmap? {
    return if (params != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        getScaledFrameAtTime(timeUs, option, dstWidth, dstHeight, params)
    } else {
        getScaledFrameAtTime(timeUs, option, dstWidth, dstHeight)
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <R> MediaMetadataRetriever.use(action: (MediaMetadataRetriever) -> R): R {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
    return try {
        action(this)
    } finally {
        release()
    }
}
