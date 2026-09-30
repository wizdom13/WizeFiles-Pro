// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.about.changelog

import android.content.Context

internal class ChangelogPreferences(context: Context) {
    // Installation-local presentation state, excluded from backup and settings export.
    private val preferences = context.getSharedPreferences("changelog", Context.MODE_PRIVATE)
    val lastSeenOrder: Int get() = preferences.getInt("last_seen_release", 0)

    fun acknowledge(order: Int) {
        preferences.edit().putInt("last_seen_release", maxOf(lastSeenOrder, order)).apply()
    }
}
