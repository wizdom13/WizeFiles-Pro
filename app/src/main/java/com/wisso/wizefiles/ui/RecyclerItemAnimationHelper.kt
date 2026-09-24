package com.wisso.wizefiles.ui

import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.view.ViewCompat
import androidx.core.view.doOnPreDraw
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView

object RecyclerItemAnimationHelper {
    private const val FADE_DURATION_MS = 180L
    private const val STAGGER_DELAY_MS = 20L
    private const val LONG_PRESS_SCALE = 1.035f
    private const val MAX_STAGGER_POSITION = 12
    private const val MAX_ENTRANCE_ANIMATED_POSITION = 40
    private const val SELECTION_SCALE_DURATION_MS = 110L

    fun applySelectionScale(view: View, selected: Boolean, animate: Boolean = true) {
        view.animate().cancel()
        view.setLayerType(View.LAYER_TYPE_NONE, null)
        val target = if (selected) LONG_PRESS_SCALE else 1f
        if (!animate || !view.isShown || !view.isAttachedToWindow) {
            view.scaleX = target
            view.scaleY = target
            view.setLayerType(View.LAYER_TYPE_NONE, null)
            return
        }
        view.animate()
            .scaleX(target)
            .scaleY(target)
            .setDuration(SELECTION_SCALE_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { view.setLayerType(View.LAYER_TYPE_NONE, null) }
            .start()
    }

    fun applyFadeIn(view: View, adapterPosition: Int) {
        if (adapterPosition == RecyclerView.NO_POSITION) return
        view.animate().cancel()
        val alreadyAnimated = view.getTag(com.wisso.wizefiles.R.id.tag_item_animated) == true
        if (alreadyAnimated) return
        if (!view.isAttachedToWindow || !view.isShown || adapterPosition > MAX_ENTRANCE_ANIMATED_POSITION) {
            view.alpha = 1f
            view.setTag(com.wisso.wizefiles.R.id.tag_item_animated, true)
            view.setLayerType(View.LAYER_TYPE_NONE, null)
            return
        }
        view.alpha = 0f
        view.doOnPreDraw {
            if (!view.isAttachedToWindow || !view.isShown) {
                view.alpha = 1f
                view.setTag(com.wisso.wizefiles.R.id.tag_item_animated, true)
                view.setLayerType(View.LAYER_TYPE_NONE, null)
                return@doOnPreDraw
            }
            view.animate()
                .alpha(1f)
                .setStartDelay((adapterPosition.coerceAtLeast(0).coerceAtMost(MAX_STAGGER_POSITION) * STAGGER_DELAY_MS))
                .setDuration(FADE_DURATION_MS)
                .setInterpolator(FastOutSlowInInterpolator())
                .withEndAction {
                    view.setTag(com.wisso.wizefiles.R.id.tag_item_animated, true)
                    view.setLayerType(View.LAYER_TYPE_NONE, null)
                }
                .start()
        }
    }

    fun clearAnimatedTag(view: View) {
        view.animate().cancel()
        view.setTag(com.wisso.wizefiles.R.id.tag_item_animated, false)
        view.alpha = 1f
        view.scaleX = 1f
        view.scaleY = 1f
        view.setLayerType(View.LAYER_TYPE_NONE, null)
    }

    fun assignTransitionName(view: View, stableKey: String) {
        ViewCompat.setTransitionName(view, "shared_thumb_$stableKey")
    }

    fun smoothScrollToPosition(recyclerView: RecyclerView, targetPosition: Int) {
        if (recyclerView.itemAnimator?.isRunning == true) return
        val layoutManager = recyclerView.layoutManager ?: return
        val smoothScroller = object : LinearSmoothScroller(recyclerView.context) {
            override fun calculateSpeedPerPixel(displayMetrics: android.util.DisplayMetrics): Float =
                90f / displayMetrics.densityDpi
        }
        smoothScroller.targetPosition = targetPosition.coerceAtLeast(0)
        layoutManager.startSmoothScroll(smoothScroller)
    }

}
