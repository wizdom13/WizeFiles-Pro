package com.wisso.wizefiles.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import androidx.lifecycle.Observer
import com.wisso.wizefiles.R
import com.wisso.wizefiles.settings.Settings
import com.wisso.wizefiles.util.getColorByAttr
import kotlin.math.min

class FileIconShapeView : DisabledAlphaImageView {
    enum class VisualStyle {
        PLAIN,
        BUILT_IN_ICON,
        THUMBNAIL,
        OUTLINE,
        APP_ICON
    }

    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var maskPath = Path()
    private var appIconMaskPath = Path()
    private var observedShape = FileIconShape.DEFAULT
    private var isObservingShape = false
    private var showPrimaryOutline = false

    private val shapeObserver = Observer<String> { value ->
        observedShape = FileIconShape.fromStableId(value)
        rebuildMask()
    }

    var shapeOverride: FileIconShape? = null
        set(value) {
            field = value
            rebuildMask()
        }

    var visualStyle: VisualStyle = VisualStyle.PLAIN
        set(value) {
            field = value
            updateContentPresentation()
        }

    constructor(context: Context) : super(context) {
        initialize(null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize(attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr) {
        initialize(attrs)
    }

    private fun initialize(attrs: AttributeSet?) {
        if (attrs == null) return
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.FileIconShapeView)
        visualStyle = VisualStyle.entries.getOrElse(
            typedArray.getInt(R.styleable.FileIconShapeView_fileIconVisualStyle, 0)
        ) { VisualStyle.PLAIN }
        showPrimaryOutline = typedArray.getBoolean(
            R.styleable.FileIconShapeView_fileIconShowPrimaryOutline,
            false
        )
        typedArray.recycle()
    }

    fun showBuiltInIcon() {
        visualStyle = VisualStyle.BUILT_IN_ICON
    }

    fun showThumbnail() {
        visualStyle = VisualStyle.THUMBNAIL
    }

    fun showPlainImage() {
        visualStyle = VisualStyle.PLAIN
    }

    fun showAppIcon() {
        visualStyle = VisualStyle.APP_ICON
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode && !isObservingShape) {
            isObservingShape = true
            Settings.FILE_ICON_SHAPE.observeForever(shapeObserver)
        }
    }

    override fun onDetachedFromWindow() {
        if (isObservingShape) {
            Settings.FILE_ICON_SHAPE.removeObserver(shapeObserver)
            isObservingShape = false
        }
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        rebuildMask()
    }

    override fun onDraw(canvas: Canvas) {
        when (visualStyle) {
            VisualStyle.PLAIN -> super.onDraw(canvas)
            VisualStyle.BUILT_IN_ICON -> {
                shapePaint.color = context.getColorByAttr(
                    com.google.android.material.R.attr.colorPrimaryContainer
                )
                canvas.drawPath(maskPath, shapePaint)
                drawMaskedContent(canvas, BUILT_IN_ICON_CONTENT_SCALE)
                if (showPrimaryOutline) {
                    drawPrimaryOutline(canvas)
                }
            }
            VisualStyle.THUMBNAIL -> {
                drawMaskedContent(canvas)
            }
            VisualStyle.APP_ICON -> {
                drawCircularContent(canvas)
            }
            VisualStyle.OUTLINE -> {
                outlinePaint.color = context.getColorByAttr(androidx.appcompat.R.attr.colorControlNormal)
                outlinePaint.alpha = OUTLINE_ALPHA
                outlinePaint.strokeWidth = resources.displayMetrics.density
                canvas.drawPath(maskPath, outlinePaint)
            }
        }
    }

    private fun drawMaskedContent(canvas: Canvas, contentScale: Float = 1f) {
        val saveCount = canvas.save()
        canvas.clipPath(maskPath)
        if (contentScale != 1f) {
            canvas.scale(contentScale, contentScale, width / 2f, height / 2f)
        }
        super.onDraw(canvas)
        canvas.restoreToCount(saveCount)
    }

    private fun drawCircularContent(canvas: Canvas) {
        val saveCount = canvas.save()
        canvas.clipPath(appIconMaskPath)
        super.onDraw(canvas)
        canvas.restoreToCount(saveCount)
    }

    private fun drawPrimaryOutline(canvas: Canvas) {
        outlinePaint.color = context.getColorByAttr(
            com.google.android.material.R.attr.colorPrimary
        )
        outlinePaint.alpha = 255
        outlinePaint.strokeWidth =
            resources.displayMetrics.density * PRIMARY_OUTLINE_STROKE_WIDTH_DP

        // Clip the centered stroke to the mask so the visible border is exactly 1 dp and
        // never extends beyond the content shape or the view bounds.
        val saveCount = canvas.save()
        canvas.clipPath(maskPath)
        canvas.drawPath(maskPath, outlinePaint)
        canvas.restoreToCount(saveCount)
    }

    private fun rebuildMask() {
        maskPath = (shapeOverride ?: observedShape).createPath(width, height)
        appIconMaskPath = Path().apply {
            addCircle(
                width / 2f,
                height / 2f,
                min(width, height) / 2f,
                Path.Direction.CW
            )
        }
        invalidate()
    }

    private fun updateContentPresentation() {
        imageTintList = if (visualStyle == VisualStyle.BUILT_IN_ICON) {
            ColorStateList.valueOf(
                context.getColorByAttr(
                    com.google.android.material.R.attr.colorOnPrimaryContainer
                )
            )
        } else {
            null
        }
        invalidate()
    }

    companion object {
        private const val BUILT_IN_ICON_CONTENT_SCALE = 0.6f
        private const val OUTLINE_ALPHA = 41
        private const val PRIMARY_OUTLINE_STROKE_WIDTH_DP = 2f
    }
}
