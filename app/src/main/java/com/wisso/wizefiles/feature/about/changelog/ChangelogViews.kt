// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.about.changelog

import android.content.Context
import android.text.method.LinkMovementMethod
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.text.HtmlCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR

internal const val CHANGELOG_ASSET = "changelogs.html"

internal fun changelogTextView(context: Context, html: String, dialog: Boolean): NestedScrollView {
    val density = context.resources.displayMetrics.density
    val body = html.substringAfter("<body>", html).substringBefore("</body>")
        .replace(Regex("<nav\\b.*?</nav>", RegexOption.DOT_MATCHES_ALL), "")
        .replace("<li>", "<p>• ").replace("</li>", "</p>")
    val text = AppCompatTextView(context).apply {
        setTextAppearance(MaterialR.style.TextAppearance_Material3_BodyMedium)
        setTextColor(MaterialColors.getColor(this, MaterialR.attr.colorOnSurface))
        setLinkTextColor(MaterialColors.getColor(this, MaterialR.attr.colorPrimary))
        setPadding((24 * density).toInt(), (8 * density).toInt(), (24 * density).toInt(), (24 * density).toInt())
        setText(HtmlCompat.fromHtml(body, HtmlCompat.FROM_HTML_MODE_LEGACY))
        movementMethod = LinkMovementMethod.getInstance()
    }
    return NestedScrollView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            if (dialog) (context.resources.displayMetrics.heightPixels * 0.5f).toInt()
            else ViewGroup.LayoutParams.MATCH_PARENT
        )
        addView(text)
    }
}
