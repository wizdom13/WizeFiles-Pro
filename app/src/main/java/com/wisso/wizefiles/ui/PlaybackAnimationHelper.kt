package com.wisso.wizefiles.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.SeekBar
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import java.util.WeakHashMap

object PlaybackAnimationHelper {
    private val progressAnimators = WeakHashMap<SeekBar, ValueAnimator>()
    private val artworkAnimators = WeakHashMap<ImageView, ObjectAnimator>()

    fun animatePlayPauseButton(button: View) {
        button.animate().cancel()
        button.alpha = 1f
        button.scaleX = 0.9f
        button.scaleY = 0.9f
        button.rotation = 0f
        button.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        button.animate()
            .scaleX(1f)
            .scaleY(1f)
            .rotationBy(10f)
            .alpha(1f)
            .setInterpolator(FastOutSlowInInterpolator())
            .setDuration(140L)
            .withEndAction {
                button.rotation = 0f
                button.setLayerType(View.LAYER_TYPE_NONE, null)
            }
            .start()
    }

    fun animateSeekBarProgress(seekBar: SeekBar, target: Int, shouldAnimate: Boolean) {
        progressAnimators.remove(seekBar)?.cancel()
        if (!shouldAnimate) {
            seekBar.progress = target
            return
        }
        val start = seekBar.progress
        if (start == target) return
        ValueAnimator.ofInt(start, target).apply {
            duration = 180L
            interpolator = LinearInterpolator()
            addUpdateListener { seekBar.progress = it.animatedValue as Int }
            start()
            progressAnimators[seekBar] = this
        }
    }

    fun updateArtworkPlaybackMotion(artwork: ImageView, isPlaying: Boolean) {
        if (!isPlaying) {
            artworkAnimators.remove(artwork)?.cancel()
            artwork.animate().cancel()
            artwork.scaleX = 1f
            artwork.scaleY = 1f
            artwork.rotation = 0f
            artwork.alpha = 1f
            artwork.setLayerType(View.LAYER_TYPE_NONE, null)
            return
        }
        if (artworkAnimators[artwork]?.isRunning == true) return
        artwork.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        ObjectAnimator.ofFloat(artwork, View.ROTATION, -1.2f, 1.2f).apply {
            duration = 3200L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            start()
            artworkAnimators[artwork] = this
        }
    }

    fun clearViewAnimations(view: View) {
        if (view is SeekBar) progressAnimators.remove(view)?.cancel()
        if (view is ImageView) artworkAnimators.remove(view)?.cancel()
        view.animate().cancel()
        view.rotation = 0f
        view.scaleX = 1f
        view.scaleY = 1f
        view.alpha = 1f
        view.setLayerType(View.LAYER_TYPE_NONE, null)
    }
}
