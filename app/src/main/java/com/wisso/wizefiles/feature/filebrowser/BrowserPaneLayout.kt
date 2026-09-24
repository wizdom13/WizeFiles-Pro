package com.wisso.wizefiles.feature.filebrowser

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout.AttachedBehavior
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentContainerView
import com.google.android.material.color.MaterialColors
import com.wisso.wizefiles.R
import kotlin.math.roundToInt

/**
 * A two-child split layout with a touch-adjustable divider and optional fold/hinge exclusion.
 * The primary child is an existing browser content view and the secondary child hosts a pane
 * fragment. The secondary child remains present when hidden so FragmentManager can retain state.
 */
internal class BrowserPaneLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs), AttachedBehavior {
    private val density = resources.displayMetrics.density
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(
            this@BrowserPaneLayout,
            com.google.android.material.R.attr.colorOutlineVariant
        )
    }
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = MaterialColors.getColor(
            this@BrowserPaneLayout,
            com.google.android.material.R.attr.colorPrimary
        )
    }
    private val dividerVisualWidth = (2f * density).roundToInt().coerceAtLeast(1)
    private val dividerTouchWidth = (32f * density).roundToInt()
    private var draggingDivider = false
    private var dividerCenter = 0
    private var hingeBounds: Rect? = null

    lateinit var primaryView: View
        private set
    val secondaryContainer = FragmentContainerView(context).apply {
        id = R.id.browserSecondaryPaneContainer
        isVisible = false
    }

    var secondaryVisible: Boolean
        get() = secondaryContainer.isVisible
        set(value) {
            if (secondaryContainer.isVisible == value) return
            secondaryContainer.isVisible = value
            requestLayout()
            invalidate()
        }

    var activePane: BrowserPane = BrowserPane.PRIMARY
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var dividerFraction: Float = BrowserTabState.DEFAULT_DIVIDER_FRACTION
        set(value) {
            val normalized = value.coerceIn(
                BrowserTabsController.MINIMUM_DIVIDER_FRACTION,
                BrowserTabsController.MAXIMUM_DIVIDER_FRACTION
            )
            if (field == normalized) return
            field = normalized
            requestLayout()
        }

    var onDividerFractionChanged: ((Float) -> Unit)? = null

    init {
        setWillNotDraw(false)
        addView(secondaryContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setPrimaryView(view: View) {
        check(!this::primaryView.isInitialized)
        primaryView = view
        // This view replaces the primary content as the CoordinatorLayout scrolling child.
        // Preserve the original inset participation so ScrollingViewBehavior measures both
        // hierarchies identically.
        fitsSystemWindows = view.fitsSystemWindows
        addView(view, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** Preserve the app-bar scrolling and inset behavior of the wrapped primary content view. */
    override fun getBehavior(): CoordinatorLayout.Behavior<*> =
        (primaryView as AttachedBehavior).behavior

    // The wrapper participates in CoordinatorLayout inset-aware measurement, but the wrapped
    // content remains the inset consumer.
    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets = insets

    fun setVerticalHinge(windowBounds: Rect?) {
        hingeBounds = windowBounds?.let { bounds ->
            val location = IntArray(2)
            getLocationInWindow(location)
            Rect(bounds).apply { offset(-location[0], -location[1]) }
        }
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        if (!secondaryVisible) {
            primaryView.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
            )
            secondaryContainer.measure(
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
            )
            return
        }
        val split = calculateSplit(width)
        primaryView.measure(
            MeasureSpec.makeMeasureSpec(split.first, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
        secondaryContainer.measure(
            MeasureSpec.makeMeasureSpec(width - split.second, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        val height = bottom - top
        if (!secondaryVisible) {
            primaryView.layout(0, 0, width, height)
            secondaryContainer.layout(width, 0, width, height)
            dividerCenter = width
            return
        }
        val split = calculateSplit(width)
        primaryView.layout(0, 0, split.first, height)
        secondaryContainer.layout(split.second, 0, width, height)
        dividerCenter = (split.first + split.second) / 2
    }

    private fun calculateSplit(width: Int): Pair<Int, Int> {
        val hinge = hingeBounds?.takeIf {
            it.left in 1 until width && it.right in 1 until width && it.width() > 0
        }
        if (hinge != null) return hinge.left to hinge.right
        val center = (width * dividerFraction).roundToInt()
        return (center - dividerVisualWidth / 2) to (center + dividerVisualWidth / 2)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!secondaryVisible) return
        val split = calculateSplit(width)
        canvas.drawRect(split.first.toFloat(), 0f, split.second.toFloat(), height.toFloat(), dividerPaint)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!secondaryVisible) return
        val active = when (activePane) {
            BrowserPane.PRIMARY -> primaryView
            BrowserPane.SECONDARY -> secondaryContainer
        }
        canvas.drawRect(
            active.left + activePaint.strokeWidth / 2,
            active.top + activePaint.strokeWidth / 2,
            active.right - activePaint.strokeWidth / 2,
            active.bottom - activePaint.strokeWidth / 2,
            activePaint
        )
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (!secondaryVisible || hingeBounds != null) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                draggingDivider = kotlin.math.abs(event.x - dividerCenter) <= dividerTouchWidth / 2
                if (draggingDivider) parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> draggingDivider = false
        }
        return draggingDivider
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!draggingDivider) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                dividerFraction = event.x / width.coerceAtLeast(1).toFloat()
                onDividerFractionChanged?.invoke(dividerFraction)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggingDivider = false
                parent.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
}
