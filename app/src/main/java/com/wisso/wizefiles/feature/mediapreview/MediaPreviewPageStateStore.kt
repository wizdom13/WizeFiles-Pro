// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.mediapreview

import androidx.lifecycle.ViewModel

internal data class MediaPreviewPlaybackState(
    val positionMs: Long = 0L,
    val playWhenReady: Boolean = true,
    val speed: Float = 1f
)

internal class MediaPreviewPageStateStore {
    private val rotations = mutableMapOf<Int, Float>()
    private val playbackStates = mutableMapOf<Int, MediaPreviewPlaybackState>()

    fun rotationFor(index: Int): Float = rotations[index] ?: 0f

    fun setRotation(index: Int, rotation: Float) {
        rotations[index] = normalizeRotation(rotation)
    }

    fun rotateClockwise(index: Int): Float =
        normalizeRotation(rotationFor(index) + 90f).also { rotations[index] = it }

    fun playbackFor(index: Int): MediaPreviewPlaybackState =
        playbackStates[index] ?: MediaPreviewPlaybackState()

    fun setPlayback(index: Int, state: MediaPreviewPlaybackState) {
        playbackStates[index] = state.copy(
            positionMs = state.positionMs.coerceAtLeast(0L),
            speed = state.speed.coerceIn(MIN_SPEED, MAX_SPEED)
        )
    }

    private fun normalizeRotation(rotation: Float): Float =
        ((rotation % FULL_ROTATION) + FULL_ROTATION) % FULL_ROTATION

    private companion object {
        const val FULL_ROTATION = 360f
        const val MIN_SPEED = 0.1f
        const val MAX_SPEED = 5f
    }
}

internal class MediaPreviewStateViewModel : ViewModel() {
    val pageStates = MediaPreviewPageStateStore()
}
