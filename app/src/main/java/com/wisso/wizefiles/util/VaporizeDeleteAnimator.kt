package com.wisso.wizefiles.util

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.view.doOnPreDraw
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt
import kotlin.random.Random

object VaporizeDeleteAnimator {
    private const val DURATION_MS = 420L
    private const val PARTICLE_SPACING_DP = 10f
    private const val PARTICLE_SIZE_DP = 3.2f

    fun animatePositions(
        recyclerView: RecyclerView,
        positions: Set<Int>,
        onComplete: () -> Unit
    ) {
        if (positions.isEmpty()) {
            onComplete()
            return
        }
        val host = recyclerView.rootView as? ViewGroup ?: run {
            onComplete()
            return
        }
        val targetsByPos = positions.associateWith { pos -> recyclerView.findViewHolderForAdapterPosition(pos)?.itemView }
        val visibleTargets = targetsByPos.values.filterNotNull()
        if (visibleTargets.isEmpty()) {
            onComplete()
            return
        }

        val overlayView = ParticleOverlayView(recyclerView)
        host.addView(overlayView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        targetsByPos.entries.sortedBy { it.key }.forEachIndexed { index, (_, view) ->
            if (view != null) {
                val particleTarget = view
                val addParticles = {
                    overlayView.addParticlesFrom(particleTarget, index)
                    view.animate().cancel()
                    val fadeDuration = (DURATION_MS * 0.72f).roundToInt().toLong()
                    view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    view.animate().alpha(0f).setDuration(fadeDuration).start()
                }
                if (particleTarget.width <= 0 || particleTarget.height <= 0) {
                    particleTarget.doOnPreDraw { addParticles() }
                } else {
                    addParticles()
                }
            }
        }

        if (!overlayView.hasParticles()) {
            host.removeView(overlayView)
            visibleTargets.forEach { view ->
                view.alpha = 1f
                view.scaleX = 1f
                view.scaleY = 1f
                view.setLayerType(View.LAYER_TYPE_NONE, null)
            }
            onComplete()
            return
        }

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DURATION_MS
            interpolator = AccelerateInterpolator(1.15f)
            addUpdateListener {
                overlayView.progress = it.animatedFraction
                overlayView.invalidate()
            }
            doOnEnd {
                visibleTargets.forEach { view ->
                view.alpha = 1f
                view.scaleX = 1f
                view.scaleY = 1f
                view.setLayerType(View.LAYER_TYPE_NONE, null)
            }
                host.removeView(overlayView)
                onComplete()
            }
        }
        overlayView.animator = animator
        animator.start()
    }

    private class ParticleOverlayView(anchor: View) : View(anchor.context) {
        var progress: Float = 0f
        var animator: ValueAnimator? = null
        private val particles = mutableListOf<Particle>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val anchorLocation = IntArray(2)

        init {
            anchor.rootView.getLocationOnScreen(anchorLocation)
            setWillNotDraw(false)
            isClickable = false
            isFocusable = false
        }

        fun hasParticles(): Boolean = particles.isNotEmpty()

        fun addParticlesFrom(itemView: View, staggerIndex: Int) {
            val bitmap = itemView.toBitmapSampled() ?: return
            val location = IntArray(2)
            itemView.getLocationOnScreen(location)
            val offsetX = (location[0] - anchorLocation[0]).toFloat()
            val offsetY = (location[1] - anchorLocation[1]).toFloat()
            val density = resources.displayMetrics.density
            val spacing = (PARTICLE_SPACING_DP * density).roundToInt().coerceAtLeast(4)

            for (x in 0 until bitmap.width step spacing) {
                for (y in 0 until bitmap.height step spacing) {
                    val color = bitmap.getPixel(x, y)
                    if (Color.alpha(color) < 28) continue
                    val normalizedX = x.toFloat() / bitmap.width.toFloat()
                    val direction = if (normalizedX > 0.5f) 1f else -1f
                    val driftX = direction * randomRange(28f, 120f, staggerIndex)
                    val driftY = randomRange(-105f, 62f, staggerIndex)
                    val swirl = randomRange(-52f, 52f, staggerIndex)
                    val size = PARTICLE_SIZE_DP * density
                    particles += Particle(
                        startX = offsetX + x,
                        startY = offsetY + y,
                        color = color,
                        size = size,
                        driftX = driftX,
                        driftY = driftY,
                        swirl = swirl,
                        delay = (staggerIndex * 0.04f).coerceAtMost(0.2f),
                        spin = 0f
                    )
                }
            }
            bitmap.recycle()
        }

        override fun onDetachedFromWindow() {
            animator?.cancel()
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            for (particle in particles) {
                val localProgress = ((progress - particle.delay) / (1f - particle.delay)).coerceIn(0f, 1f)
                if (localProgress <= 0f) continue
                val eased = localProgress * localProgress
                val alpha = ((1f - localProgress) * Color.alpha(particle.color)).roundToInt().coerceIn(0, 255)
                if (alpha == 0) continue
                paint.color = particle.color
                paint.alpha = alpha
                val x = particle.startX + particle.driftX * eased + particle.swirl * (1f - localProgress)
                val y = particle.startY + particle.driftY * eased - 18f * localProgress
                val wobbleX = particle.spin * localProgress * (1f - localProgress)
                canvas.drawCircle(x + wobbleX, y, particle.size * (1f - 0.25f * localProgress), paint)
            }
        }
    }

    private data class Particle(
        val startX: Float,
        val startY: Float,
        val color: Int,
        val size: Float,
        val driftX: Float,
        val driftY: Float,
        val swirl: Float,
        val delay: Float,
        val spin: Float
    )

    private fun View.toBitmapSampled(): Bitmap? {
        if (width <= 0 || height <= 0) return null
        return runCatching {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                draw(canvas)
            }
        }.getOrNull()
    }

    private fun randomRange(min: Float, max: Float, seedSalt: Int): Float {
        val random = Random(System.nanoTime() + seedSalt)
        return min + random.nextFloat() * (max - min)
    }
}
