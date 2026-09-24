package com.wisso.wizefiles.ui

import android.graphics.Color
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.wisso.wizefiles.R
import com.wisso.wizefiles.feature.filebrowser.FastScrollLabelProvider
import kotlin.math.abs
import kotlin.math.roundToInt

class RecyclerViewFastScrollPopup(
    private val recyclerView: RecyclerView,
    overlayParent: ViewGroup
) {
    private val popupView = TextView(recyclerView.context).apply {
        background = ContextCompat.getDrawable(context, R.drawable.bg_fast_scroll_popup)
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(8), dp(12), dp(8))
        minWidth = dp(48)
        alpha = 0f
        isVisible = false
    }

    private val hideRunnable = Runnable { hidePopup() }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        private var lastOffset = 0
        private var lastTimestamp = 0L

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (recyclerView.adapter?.itemCount ?: 0 <= 0) {
                hidePopupImmediately()
                return
            }
            val offset = recyclerView.computeVerticalScrollOffset()
            val now = SystemClock.elapsedRealtime()
            val offsetDelta = abs(offset - lastOffset)
            val timeDelta = (now - lastTimestamp).coerceAtLeast(1)
            val isRapidScroll = offsetDelta > dp(16) && timeDelta <= 100
            if (isRapidScroll || isDraggingOnScrollbar) {
                updatePopupText()
                showPopup()
                scheduleHide()
            }
            lastOffset = offset
            lastTimestamp = now
        }
    }

    private var isDraggingOnScrollbar = false

    private val touchListener = View.OnTouchListener { _, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDraggingOnScrollbar = isTouchNearVerticalScrollbar(event.x)
                if (isDraggingOnScrollbar) {
                    updatePopupText()
                    showPopup()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDraggingOnScrollbar) {
                    updatePopupText()
                    showPopup()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDraggingOnScrollbar) {
                    scheduleHide()
                }
                isDraggingOnScrollbar = false
            }
        }
        false
    }

    init {
        overlayParent.addView(
            popupView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.CENTER_VERTICAL
            ).apply {
                marginEnd = dp(32)
            }
        )
        recyclerView.addOnScrollListener(scrollListener)
        recyclerView.setOnTouchListener(touchListener)
    }

    fun setBottomInset(bottomInset: Int) {
        popupView.updateLayoutParams<FrameLayout.LayoutParams> {
            bottomMargin = bottomInset
        }
    }

    fun detach() {
        recyclerView.removeCallbacks(hideRunnable)
        recyclerView.removeOnScrollListener(scrollListener)
        recyclerView.setOnTouchListener(null)
        (popupView.parent as? ViewGroup)?.removeView(popupView)
    }

    private fun isTouchNearVerticalScrollbar(x: Float): Boolean {
        val thumbZoneWidth = dp(48)
        return x >= recyclerView.width - thumbZoneWidth
    }

    private fun updatePopupText() {
        val adapter = recyclerView.adapter as? FastScrollLabelProvider ?: return
        val itemCount = recyclerView.adapter?.itemCount ?: return
        if (itemCount == 0) {
            hidePopupImmediately()
            return
        }
        val range = recyclerView.computeVerticalScrollRange()
        val extent = recyclerView.computeVerticalScrollExtent()
        val offset = recyclerView.computeVerticalScrollOffset()
        val maxOffset = (range - extent).coerceAtLeast(1)
        val ratio = offset.toFloat() / maxOffset
        val targetPosition = (ratio * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
        val text = adapter.getFastScrollPopupText(targetPosition)
        if (text.isNullOrEmpty()) {
            hidePopupImmediately()
            return
        }
        popupView.text = text
    }

    private fun showPopup() {
        recyclerView.removeCallbacks(hideRunnable)
        if (!popupView.isVisible) {
            popupView.isVisible = true
            popupView.animate().cancel()
            popupView.animate().alpha(1f).setDuration(120).start()
        }
    }

    private fun scheduleHide() {
        recyclerView.removeCallbacks(hideRunnable)
        recyclerView.postDelayed(hideRunnable, 500)
    }

    private fun hidePopup() {
        popupView.animate().cancel()
        popupView.animate().alpha(0f).setDuration(160).withEndAction {
            popupView.isVisible = false
        }.start()
    }

    private fun hidePopupImmediately() {
        recyclerView.removeCallbacks(hideRunnable)
        popupView.animate().cancel()
        popupView.alpha = 0f
        popupView.isVisible = false
    }

    private fun dp(value: Int): Int =
        (value * recyclerView.resources.displayMetrics.density).roundToInt()
}
