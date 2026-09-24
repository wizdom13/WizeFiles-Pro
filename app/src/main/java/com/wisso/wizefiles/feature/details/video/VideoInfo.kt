// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.details.video

import android.util.Size
import java.time.Duration
import java.time.Instant

// @see com.android.providers.media.scan.ModernMediaScanner.scanItemVideo
// @see com.android.documentsui.inspector.MediaView.showVideoData
// @see https://github.com/GNOME/nautilus/blob/c73ad94a72f8e9a989b01858018de74182d17f0e/extensions/audio-video-properties/bacon-video-widget-properties.c#L89
class VideoInfo(
    val title: String?,
    val dimensions: Size?,
    val duration: Duration?,
    val date: Instant?,
    val location: Pair<Float, Float>?,
    val bitRate: Long?
)
