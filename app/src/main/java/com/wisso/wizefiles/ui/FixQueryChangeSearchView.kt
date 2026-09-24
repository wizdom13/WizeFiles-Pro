// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.ui

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import androidx.annotation.AttrRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.wisso.wizefiles.util.hideSoftInput

class FixQueryChangeSearchView : FixLayoutSearchView {
    var shouldIgnoreQueryChange = false
        private set

    private var preserveSearchUntilUptimeMillis = 0L

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    fun hideImePreservingSearch(): Boolean {
        val searchTextView = findViewById<View>(androidx.appcompat.R.id.search_src_text)
        val now = SystemClock.uptimeMillis()
        if (isImeVisible() || searchTextView.hasFocus()) {
            preserveSearchUntilUptimeMillis = now + IME_DISMISSAL_GRACE_MILLIS
            super.clearFocus()
            hideKeyboard(searchTextView)
            return true
        }
        return now < preserveSearchUntilUptimeMillis
    }

    override fun clearFocus() {
        val searchTextView = findViewById<View>(androidx.appcompat.R.id.search_src_text)
        val wasEditing = isImeVisible() || searchTextView.hasFocus()
        super.clearFocus()
        if (wasEditing) {
            preserveSearchUntilUptimeMillis =
                SystemClock.uptimeMillis() + IME_DISMISSAL_GRACE_MILLIS
        }
    }

    override fun setIconified(iconify: Boolean) {
        shouldIgnoreQueryChange = true
        super.setIconified(iconify)
        shouldIgnoreQueryChange = false
    }

    override fun onActionViewCollapsed() {
        shouldIgnoreQueryChange = true
        super.onActionViewCollapsed()
        preserveSearchUntilUptimeMillis = 0L
        shouldIgnoreQueryChange = false
    }

    override fun onActionViewExpanded() {
        shouldIgnoreQueryChange = true
        super.onActionViewExpanded()
        preserveSearchUntilUptimeMillis = 0L
        shouldIgnoreQueryChange = false
    }

    @Suppress("DEPRECATION")
    private fun hideKeyboard(searchTextView: View) {
        hideSoftInput()
        ViewCompat.getWindowInsetsController(this)?.hide(WindowInsetsCompat.Type.ime())
        searchTextView.hideSoftInput()
        ViewCompat.getWindowInsetsController(searchTextView)?.hide(WindowInsetsCompat.Type.ime())
    }

    private fun isImeVisible(): Boolean {
        return ViewCompat.getRootWindowInsets(this)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true
    }

    private companion object {
        const val IME_DISMISSAL_GRACE_MILLIS = 500L
    }
}
