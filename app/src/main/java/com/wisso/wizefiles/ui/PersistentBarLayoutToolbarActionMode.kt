// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.ui

import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.wisso.wizefiles.util.shortAnimTime

class PersistentBarLayoutToolbarActionMode(
    private val persistentBarLayout: PersistentBarLayout,
    bar: ViewGroup,
    toolbar: Toolbar
) : ToolbarActionMode(bar, toolbar) {
    private var animationSequence = 0

    override fun show(bar: ViewGroup, animate: Boolean) {
        val sequence = ++animationSequence
        bar.animate().cancel()
        persistentBarLayout.showBar(bar, false)
        if (!animate || bar.width == 0) {
            bar.translationX = 0f
            return
        }

        val leftMargin = (bar.layoutParams as? ViewGroup.MarginLayoutParams)?.leftMargin ?: 0
        val offscreenDistance = (bar.width + leftMargin).toFloat()
        bar.translationX = -offscreenDistance
        bar.animate()
            .translationX(0f)
            .setDuration(bar.context.shortAnimTime.toLong())
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction {
                if (animationSequence == sequence) {
                    bar.translationX = 0f
                }
            }
            .start()
    }

    override fun hide(bar: ViewGroup, animate: Boolean) {
        val sequence = ++animationSequence
        bar.animate().cancel()
        if (!animate || bar.width == 0) {
            bar.translationX = 0f
            persistentBarLayout.hideBar(bar, false)
            return
        }

        val leftMargin = (bar.layoutParams as? ViewGroup.MarginLayoutParams)?.leftMargin ?: 0
        val offscreenDistance = (bar.width + leftMargin).toFloat()
        bar.animate()
            .translationX(-offscreenDistance)
            .setDuration(bar.context.shortAnimTime.toLong())
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction {
                if (animationSequence == sequence) {
                    persistentBarLayout.hideBar(bar, false)
                    bar.translationX = 0f
                }
            }
            .start()
    }
}
