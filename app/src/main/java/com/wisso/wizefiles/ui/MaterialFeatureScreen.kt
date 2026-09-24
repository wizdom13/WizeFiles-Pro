// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.R as MaterialR
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator

/**
 * Shared Material 3 shell for small, state-driven feature screens.
 *
 * This keeps toolbar, insets, spacing, typography, cards and actions consistent with
 * the rest of WizeFiles while allowing a feature to rebuild content from live state.
 */
class MaterialFeatureScreen(
    private val activity: AppCompatActivity,
    @StringRes titleRes: Int
) {
    private val content = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(32))
    }
    private val toolbar = MaterialToolbar(activity).apply {
        setTitle(titleRes)
    }
    private val scrollView = NestedScrollView(activity).apply {
        id = View.generateViewId()
        clipToPadding = false
        isFillViewport = true
        addView(content, matchWrap())
    }
    private val root = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        addView(toolbar, matchWrap())
        addView(scrollView, weighted())
    }
    private var target = content

    fun install() {
        activity.setContentView(root)
        activity.setSupportActionBar(toolbar)
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        applyInsets()
    }

    fun clear() {
        target = content
        content.removeAllViews()
    }

    fun intro(value: CharSequence, @DrawableRes iconRes: Int) {
        card {
            addCentered(AppCompatImageView(activity).apply {
                setImageResource(iconRes)
                contentDescription = null
                ImageViewCompat.setImageTintList(
                    this,
                    ColorStateList.valueOf(
                        MaterialColors.getColor(
                            this,
                            MaterialR.attr.colorPrimary,
                            Color.GRAY
                        )
                    )
                )
            }, dp(40))
            add(textView(value).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setTextAppearance(MaterialR.style.TextAppearance_Material3_BodyLarge)
                setTextColor(onSurfaceVariant(this))
            }, matchWrapMargins(top = 10))
        }
    }

    fun heading(value: CharSequence) {
        add(textView(value).apply {
            setTextAppearance(MaterialR.style.TextAppearance_Material3_TitleMedium)
        }, matchWrapMargins(top = 20, bottom = 4))
    }

    fun text(value: CharSequence) {
        add(textView(value).apply {
            setTextAppearance(MaterialR.style.TextAppearance_Material3_BodyMedium)
            setTextColor(onSurfaceVariant(this))
        }, matchWrapMargins(top = 2, bottom = 2))
    }

    fun selectable(value: CharSequence) {
        val valueView = textView(value).apply {
            setTextAppearance(MaterialR.style.TextAppearance_Material3_BodyLarge)
            setTextIsSelectable(true)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val valueCard = MaterialCardView(activity).apply {
            strokeWidth = dp(1)
            setCardBackgroundColor(Color.TRANSPARENT)
            addView(valueView, matchWrap())
        }
        add(valueCard, matchWrapMargins(top = 4, bottom = 4))
    }

    fun card(block: MaterialFeatureScreen.() -> Unit) {
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
        }
        val card = MaterialCardView(activity).apply {
            preventCornerOverlap = false
            addView(body, matchWrap())
        }
        val previous = target
        target = body
        try {
            block()
        } finally {
            target = previous
        }
        add(card, matchWrapMargins(top = 8, bottom = 4))
    }

    fun button(
        label: CharSequence,
        kind: ButtonKind = ButtonKind.PRIMARY,
        action: () -> Unit
    ): MaterialButton = buttonView(label, kind, action).also {
        add(it, matchWrapMargins(top = 8))
    }

    fun buttonView(
        label: CharSequence,
        kind: ButtonKind = ButtonKind.PRIMARY,
        action: () -> Unit
    ): MaterialButton {
        val view = when (kind) {
            ButtonKind.PRIMARY -> MaterialButton(activity)
            ButtonKind.OUTLINED ->
                MaterialButton(activity, null, MaterialR.attr.materialButtonOutlinedStyle)
        }
        return view.apply {
            text = label
            isAllCaps = false
            minHeight = dp(48)
            setOnClickListener { action() }
        }
    }

    fun indeterminateProgress() {
        add(CircularProgressIndicator(activity).apply {
            isIndeterminate = true
        }, centeredWrapMargins(top = 16))
    }

    fun horizontalProgress(progress: Int) {
        add(LinearProgressIndicator(activity).apply {
            max = 100
            setProgressCompat(progress.coerceIn(0, 100), true)
        }, matchWrapMargins(top = 12, bottom = 4))
    }

    fun addCentered(view: View, size: Int, topMargin: Int = 0) {
        add(view, LinearLayout.LayoutParams(size, size).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            this.topMargin = dp(topMargin)
        })
    }

    fun add(view: View, params: ViewGroup.LayoutParams? = null) {
        if (params == null) target.addView(view) else target.addView(view, params)
    }

    private fun textView(value: CharSequence) = TextView(activity).apply {
        text = value
    }

    private fun onSurfaceVariant(view: View) = MaterialColors.getColor(
        view,
        MaterialR.attr.colorOnSurfaceVariant,
        Color.GRAY
    )

    private fun applyInsets() {
        val initialLeft = root.paddingLeft
        val initialRight = root.paddingRight
        val initialToolbarTop = toolbar.paddingTop
        val initialScrollBottom = scrollView.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = initialLeft + bars.left, right = initialRight + bars.right)
            toolbar.updatePadding(top = initialToolbarTop + bars.top)
            scrollView.updatePadding(bottom = initialScrollBottom + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun dp(value: Int) =
        (value * activity.resources.displayMetrics.density).toInt()

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun weighted() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        0,
        1f
    )

    private fun matchWrapMargins(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }

    private fun centeredWrapMargins(top: Int = 0) =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(top)
        }

    enum class ButtonKind { PRIMARY, OUTLINED }
}
